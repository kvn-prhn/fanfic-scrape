(ns fanfic-scrape.core
  (:gen-class)
  (:require [yaml.core :as yaml]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import (java.net URI)
           (java.security MessageDigest)
           (org.jsoup Jsoup)
           (java.net URLEncoder)
           (com.mongodb ConnectionString)
           (com.mongodb.client MongoClients)
           (com.mongodb.client MongoClient)
           (org.bson Document)
           (java.io File)
           (java.util Date)))


; https://gist.github.com/jizhang/4325757
(defn url-hash-val [url]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes url))]
    (format "%032x" (BigInteger. 1 raw))))


(defn add-url-page-num [url page-num-parameter page-num]
  (let [url-uri (.toURI (io/as-url url))
        url-uri-query (.getQuery url-uri)
        url-uri-query-new (if (nil? url-uri-query)
              (string/join [page-num-parameter "=" (str page-num)])
              (string/join [url-uri-query "&" page-num-parameter "=" (str page-num)]))]
            
    (.toString (new URI  
                    (.getScheme url-uri)
                    (.getAuthority url-uri) (.getPath url-uri)
                            url-uri-query-new (.getFragment url-uri)))))


(defn process-config [config-file]
  (let [config (yaml/from-file config-file)]
    {:searches (map
                (fn [search-config]
                  {:name (get search-config "name")
                   :base-url (loop [url (get config "root_url")
                                    params (get search-config "parameters")]
                               (if (empty? params)
                                 (.toASCIIString (.toURI (io/as-url url)))
                                 (recur
                                  (string/replace
                                   url
                                   (string/join ["{" (get (first params) "key") "}"])
                                   (string/replace
                                    (URLEncoder/encode (get (first params) "value"))
                                    "+"   ; gross manual fix
                                    "%20"))
                                  (rest params))))
                   :page-num-parameter (get config "page_num_parameter")
                   :max-page-num ((fn [x] (if (not (nil? x)) x 100)) (get config "max_page_num"))
                   :cache-folder (get config "cache_folder")})
                (get config "searches"))
     :mongo-connection-string (get (get config "mongo") "connection_string")
     :mongo-db (get (get config "mongo") "db")
     :mongo-collection (get (get config "mongo") "collection")
     :cache-folder (get config "cache_folder")
     :root-url (get config "root_url")}))

(defn get-cached-file-name [url]
  (string/join ["cached-" (url-hash-val url) ".html"]))


(defn get-page-html [url cache-folder]
  (let [ fname (get-cached-file-name url)
        f (new File
               cache-folder
               fname)
        expected-cache-file (io/file (.toString 
                                      (.getAbsoluteFile 
                                       f)))]
    (if (.exists expected-cache-file)
      ; read the existing cache file
      (slurp expected-cache-file)
      ; get html data, write cache file, read cache file
      (let [url-content (slurp url)]
        (if (string? url-content)
          (do 
            (spit expected-cache-file url-content)
            url-content)
          ; if no content returned, return nil and print
          (do
            (println "No content from " url)
            (println url-content)
            nil))))))


(defn extract-max-page-num [html]
  (let [html-soup (Jsoup/parse html)
        elems (.select html-soup ".pagination li:not([^title]) a[href]")]
    (Integer/parseInt (.text (.last elems)))))


(defn extract-works-metadata-from-html [html]
  (let [html-soup (Jsoup/parse html)
        works-elems (.select html-soup ".work.index.group li.work.blurb")]
    (map 
     (fn [work-elem]
       (let [replace-commas (fn [s] (string/trim (string/replace s #"," "")))
             parse-or-negative-one (fn [s] (if (nil? (re-matches #"[0-9]+" s)) -1 (Integer/parseInt s)))
             words-str (replace-commas (.text (.select work-elem ".stats dd.words")))
             kudos-str (replace-commas (.text (.select work-elem ".stats dd.kudos"))) 
             hits-str (replace-commas (.text (.select work-elem ".stats dd.hits")))]
         {:title (.text (.select work-elem "h4.heading a:not([rel])"))
          :author (.text (.select work-elem "h4.heading a[rel]"))
          :tags (map (fn [e] (.text e)) (.select work-elem "ul.tags li:not(.warnings)")) ; should be a list of tags
          :summary (.text (.select work-elem ".summary p"))
          :works-path (.attr (.first (.select work-elem "h4.heading a[href]")) "href")
          :language (.text (.select work-elem ".stats dd.language"))
          :words (parse-or-negative-one words-str)
          :kudos (parse-or-negative-one kudos-str)
          :hits (parse-or-negative-one hits-str)}))
     works-elems)))


(defn clear-cache-files [urls cache-folder]
  (let [expected-cache-files (map 
                              (fn [url] (io/file cache-folder (get-cached-file-name url))) 
                              urls)]
    (map 
     (fn [f] (if (.exists f) 
               (.delete f)
               false)) 
     expected-cache-files)))


(defn get-urls-all-pages [base-url page-num-parameter config-max-page-num cache-folder]
  (let [first-page-url (add-url-page-num base-url page-num-parameter 1)
        first-page-html (get-page-html first-page-url cache-folder)
        max-page-num (min config-max-page-num (extract-max-page-num first-page-html))]
    (map 
     (fn [page-num]
       (add-url-page-num 
        base-url 
        page-num-parameter 
        page-num))
     (range 1 (inc max-page-num)))))


(defn get-urls-all-pages-config [config-map]
  (flatten 
   (map
    (fn [config-search]
      (get-urls-all-pages
               (get config-search :base-url)
               (get config-search :page-num-parameter)
               (get config-search :max-page-num)
               (get config-search :cache-folder)))
            (get config-map :searches))))


(defn open-mongo-collection-config [config-map]
  (let [mongo-client (MongoClients/create (get config-map :mongo-connection-string))
        db (.getDatabase mongo-client (get config-map :mongo-db))]
    (.getCollection db (get config-map :mongo-collection))))


(defn works-metadata-to-mongo-doc [metadata config-map]
  (let [url-uri (.toURI (io/as-url (.substring 
                                    (get config-map :root-url)
                                    0
                                    (.indexOf (get config-map :root-url) "/" (+ 3 (.length "https://"))))))]
     (new Document {"title" (get metadata :title)
                    "author" (get metadata :author)
                    "tags" (get metadata :tags)
                    "summary" (get metadata :summary)
                    "created" (.toString (.toInstant (new Date)))
                    "url" (.toString (new URI
                                          (.getScheme url-uri)
                                          (.getAuthority url-uri) (get metadata :works-path)
                                          (.getQuery url-uri) (.getFragment url-uri)))
                    "language" (get metadata :language)
                    "words" (get metadata :words)
                    "kudos" (get metadata :kudos)
                    "hits" (get metadata :hits)})))
   


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (if (>= (count args) 1)
    (let [config-file (nth args 0)
          config-map (process-config config-file)
          ; generate the list of urls, limit by max page number
          urls-list (get-urls-all-pages-config config-map)
          ; get the html pages from the urls list
          urls-list-html (map (fn [url] (get-page-html url (get config-map :cache-folder))) urls-list)
          urls-list-metadata (flatten (map extract-works-metadata-from-html urls-list-html))
          metadata-docs (map (fn [x] (works-metadata-to-mongo-doc x config-map)) urls-list-metadata)
          mongo-collection (open-mongo-collection-config config-map)]


      (println (count metadata-docs))
      (println config-map)
      ; (println (get config-map :cache-folder))
      ; get data from all urls

      ; push data to mongo db
      (.insertMany mongo-collection metadata-docs)
      
      (println "done"))
    (println "Error: Must provide path to config file")))
    



