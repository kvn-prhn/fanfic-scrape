# fanfic-scrape

Scrapes AO3 for metadata about fanfictions and pushes the data into a MongoDB database.

## Installation

Download [leiningen](https://leiningen.org/) and a [MongoDB server](https://www.mongodb.com/try/download/community)


## Usage

Make sure the MongoDB server is running

```
   $ mongod --dbpath C:\data\db
```

Run unit tests with
```
    $ lein test
```


Write a config YAML file and run

```
    $ java -jar fanfic-scrape-0.1.0-standalone.jar config.yaml
```

or

```
    $ lein run config.yaml
```

Where `config.yaml` is a YAML config file. 

Data in MongoDB will have fields for the title, author, summary, tags, url, and date it was added to the database.

```
    $ mongo
    > use fanfics
    switched to db fanfics
    > db.works.count() 
    2397
    > db.works.find({ "tags" : "Kururugi Suzaku"}).count()
    1429
    > db.works.aggregate([{ $unwind : { path : "$author" } }, 
                          { $group : { _id : "$author", count: { $sum: 1 } } }, 
                          { $sort : { count : -1 }} ])
    { "_id" : "Divano_Messiah", "count" : 80 }
    { "_id" : "orphan_account", "count" : 45 }
    { "_id" : "NeoDiji", "count" : 37 }
```



## Example config YAML

```yaml
# base URL it uses
root_url: "https://archiveofourown.org/tags/{tag}/works"
# list of searches and values to use in the root_url
searches:
  - name: "Code Geass fics"
    parameters:
    - key: "tag"
      value: "Code Geass" 
# the name of the page parameter it will add to the url
page_num_parameter: "page"
# the script will save .html files to the cache_folder as it scrapes
# the site. Each URL gets a unique cache file name.
cache_folder: "C:\\path\\to\\cache\\folder"
# MongoDB server connection info
mongo:
  connection_string: "mongodb://localhost:27017"
  db: "fanfics"
  collection: "works"
```

### Bugs

* tags with non-ASCII characters aren't being downloaded correctly
* When downloading a lot of pages, responses might give errors because there are too many requests too fast

## License

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
