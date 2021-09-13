(ns tsdl.downloader
  (:require [clj-http.client :as http]
            [clj-http.cookies :as cookies]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [hickory.core :as hic]
            [hickory.select :as s]
            [lambdaisland.uri :as uri :refer [uri]])
  (:import (java.io File)
           #_(org.apache.commons.imaging Imaging)
           #_(org.apache.commons.imaging.formats.jpeg.exif ExifRewriter)
           #_(org.apache.commons.imaging.formats.tiff.write TiffOutputSet)))

(set! *warn-on-reflection* true)

(def global-cookie-store (cookies/cookie-store))

(def base-uri
  (assoc (uri nil)
         :scheme "https"
         :host "tealswan.com"))

(def login-uri (assoc base-uri :path "/login"))

(def paintings-uri (assoc base-uri :path "/paintings"))

(defn get-body
  [uri]
  (println (str "Fetching body of " uri))
  (-> uri
      str
      (http/get {:cookie-store global-cookie-store})
      :body))

(defn get-hickory-data
  [uri]
  (-> uri
      get-body
      hic/parse
      hic/as-hickory))

(defn get-csrf-key
  [uri]
  (-> (s/select (s/attr :name #(= % "csrfKey"))
                (get-hickory-data uri))
      first
      :attrs
      :value))

(defn csrf-key
  []
  (get-csrf-key login-uri))

(defonce logged-in? (atom false))

(defn load-cache [f]
  (edn/read-string (slurp f)))

(def cache-file (io/file "resources/cache.edn"))

(defonce cache
  (atom
   (if (.exists ^File cache-file)
     (load-cache cache-file)
     nil)))

(defn log-in
  [{:keys [username password]}]
  (let [res (http/post (str login-uri)
                       {:cookie-store     global-cookie-store
                        :form-params      {:csrfKey       (csrf-key)
                                           :auth          username
                                           :password      password
                                           :remember_me   "1"
                                           :_processLogin "usernamepassword"}
                        :throw-exceptions false})]
    (condp = (:status res)
      301 (do (println "Success!")
              (reset! logged-in? true))
      403 (do (println "You're probably already logged-in.")
              (reset! logged-in? true))
      (println "I'm not sure what happened..."))))

(defn painting-title
  [hickory-data]
  (-> (s/select (s/and (s/tag :h1)
                       (s/class "page-title"))
                hickory-data)
      first
      :content
      first
      string/trim))

(defn painting-desc
  [hickory-data]
  (->> (s/select (s/descendant (s/and (s/tag :section)
                                      (s/class "paining-det-desc"))
                               (s/tag :p)) hickory-data)
       first
       :content
       (map #(if (map? %)
               (->> % :content (apply str)) %))
       string/join
       string/trim))

(defn download-link
  [hickory-data]
  (->> (s/select (s/and (s/tag :a)
                        (s/attr "download"))
                 hickory-data)
       first
       :attrs
       :href))

(defn painting-data
  [uri]
  (let [hic (get-hickory-data uri)]
    {:title       (painting-title hic)
     :description (painting-desc hic)
     :link        (download-link hic)}))

(defn num-of-pages []
  (->> (s/select (s/class "ipsPagination_pageJump")
                 (get-hickory-data paintings-uri))
       first
       (s/select (s/tag :a))
       first
       :content
       first
       (re-find #"Page \d of (\d+)")
       last
       Integer/parseInt))

(defn painting-links [uri]
  (map #(-> (s/select (s/descendant
                       (s/tag :h2)
                       (s/tag :a)) %)
            first
            :attrs
            :href)
       (s/select (s/id "painting-list-item")
                 (get-hickory-data uri))))

(def all-painting-links
  (delay
   (apply concat
          (pmap (fn [pagenum]
                  (painting-links
                   (update paintings-uri :path #(str %1 "/page/" %2) pagenum)))
                (map inc (range (num-of-pages)))))))

(def all-painting-data
  (do (log-in @cache)
      (pmap painting-data (deref all-painting-links))))

(defn make-directory
  [^File path]
  (when-not (.exists path)
    (.mkdir path)))

(defn download-file
  [img-uri out-path]
  (clojure.java.io/copy
   (:body (http/get img-uri {:as :stream}))
   (io/file out-path)))

(defn normalize-filename [title-in]
  (-> title-in
      (string/replace #"[\"‘’-]" "")
      (string/replace #"\s+" "_")
      (string/lower-case)))

(defn download-painting
  [{:keys [link title description]}]
  (let [extension (re-find #"\.[^.]+$" link)
        images-path "./images-path"
        path (File. (str images-path "/"
                         (normalize-filename title) extension))]
    (make-directory (File. images-path))
    (if-not (.exists path)
      (do (println (str "Downloading \"" title "\"."))
          (println link)
          (download-file link path))
      (println (str "Already downloaded \"" title "\".")))))

(defn download-all-paintings []
  (let [num-of-paintings (count all-painting-data)
        download-progress (atom 0)]
    (println (str num-of-paintings " paintings to download."))
    (map #(do (download-painting %)
              (swap! download-progress inc)
              (println (str "Got " @download-progress "/" num-of-paintings)))
         all-painting-data)))
