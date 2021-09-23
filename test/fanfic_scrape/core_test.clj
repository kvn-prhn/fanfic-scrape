(ns fanfic-scrape.core-test
  (:require [clojure.test :refer :all]
            [fanfic-scrape.core :refer :all]
            [clojure.java.io :as io])
   (:import (com.mongodb.client MongoCollection)))


(deftest test-process-config
  (testing "process-config fail"
    ; https://stackoverflow.com/questions/8009829/resources-in-clojure-applications
    (let [config-map (process-config (-> "config1.yaml" io/resource io/file))]
      ; (println config-map)
      (is (map? config-map))
      (is (string? (get (first (get config-map :searches)) :base-url)))
      (is (string? (get (first (get config-map :searches)) :cache-folder)))
      (is (string? (get config-map :root-url)))
      )))


(deftest test-add-url-page-num
  (testing "add-url-page-num fail"
    (is (= (add-url-page-num "https://example.com" "page" 1) "https://example.com?page=1"))
    (is (= (add-url-page-num "https://example.com?meh=foo" "page" 1) "https://example.com?meh=foo&page=1"))
    (is (= (add-url-page-num "https://example.com?meh=foo#bweh" "page" 1) "https://example.com?meh=foo&page=1#bweh"))
    ))


(deftest test-url-hash-val
  (testing "url-hash-val fail"
    (is (string? (url-hash-val "https://example.com")))
    (is (not (= (url-hash-val "https://example.com?page=1") (url-hash-val "https://example.com?page=2"))))
    ; (println (url-hash-val "https://example.com"))
    ))


(deftest test-get-page-html
  (testing "get-page-html fail"
    (let [test-cache-folder (-> "test-cache" io/resource io/file)]
     (is (string? (get-page-html "https://www.wikipedia.org/" test-cache-folder)))
     (is (string? (get-page-html "https://archiveofourown.org/tags/Adventure/works" test-cache-folder)))
    )))


(deftest test-extract-max-page-num
  (testing "extract-max-page-num fail"
    (let [test-cache-folder (-> "test-cache" io/resource io/file)]
     (is (= 1345 (extract-max-page-num 
                  (get-page-html "https://archiveofourown.org/tags/Adventure/works" test-cache-folder))))
      
    )))


(deftest test-extract-works-metadata-from-html
  (testing "extract-works-metadata-from-html fail"
    (let [test-cache-folder (-> "test-cache" io/resource io/file)
          extract-data (extract-works-metadata-from-html
                        (get-page-html "https://archiveofourown.org/tags/Adventure/works" test-cache-folder))
          first-data (first extract-data)]
      (is (seq extract-data)) ; is this a sequence and not empty?
      (is (= (get first-data :title) "Date with the Devil" )) ; first title might be different
      (is (= (get first-data :works-path) "/works/33899812"))
    )))


(deftest test-get-urls-all-pages
  (testing "get-urls-all-pages fail"
    (let [config-map (process-config (-> "config1.yaml" io/resource io/file))
          urls-all-pages (flatten (map 
                          (fn [config-search]
                            (get-urls-all-pages
                             (get config-search :base-url)
                             (get config-search :page-num-parameter)
                             (-> (get config-search :cache-folder) io/resource io/file)))
                          (get config-map :searches)))]
      (is (seq urls-all-pages))
      (is (= 7 (count urls-all-pages)))
      )))


(deftest test-open-mongo-collection-config
  (testing "open-mongo-collection-config fail"
    (let [config-map (process-config (-> "config1.yaml" io/resource io/file))
          mongo-coll (open-mongo-collection-config config-map)]
      ; (println mongo-coll)
      (is (instance? MongoCollection mongo-coll))
      )))


(deftest test-works-metadata-to-mongo-doc
  (testing "works-metadata-to-mongo-doc fail"
    (let [config-map (process-config (-> "config1.yaml" io/resource io/file))
          test-cache-folder (-> "test-cache" io/resource io/file)
          extract-data (extract-works-metadata-from-html
                        (get-page-html "https://archiveofourown.org/tags/Adventure/works" test-cache-folder))
          first-data (first extract-data)
          doc (works-metadata-to-mongo-doc first-data config-map)]
      (println doc)
      (is (string? (.get doc "title")))
      (is (string? (.get doc "author")))
      (is (> (count (.get doc "tags")) 0))
      (is (string? (.get doc "summary")))
      (is (string? (.get doc "created")))
      (is (string? (.get doc "url")))
      )))