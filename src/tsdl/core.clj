(ns tsdl.core
  (:require [clj-http.client :as http]
            [clj-http.cookies :as cookies]
            [clj-http.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [hickory.core :as hic]
            [hickory.select :as s]
            [lambdaisland.uri :as uri :refer [uri]]))

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

(def csrf-key (get-csrf-key login-uri))

(defonce logged-in? (atom false))
(defonce cache (atom (edn/read-string (slurp "resources/cache.edn"))))

(defn log-in
  [{:keys [username password]}]
  (if-not @logged-in?
    (let [res (when-not @logged-in?
                              (http/post (str login-uri)
                                         {:cookie-store global-cookie-store
                                          :form-params {:csrfKey csrf-key
                                                        :auth username
                                                        :password password
                                                        :remember_me "1"
                                                        :_processLogin "usernamepassword"}
                                          :throw-exceptions false}))]
                    (condp = (:status res)
                      301 (do (println "Success!")
                              (reset! logged-in? true))
                      :else (println "Something weird happened.")))
      (println "Already logged in.")))

(log-in @cache)

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
    {:title (painting-title hic)
     :description (painting-desc hic)
     :link (download-link hic)}))

(def all-painting-data
  (let [num-of-pages
        (->> (s/select (s/class "ipsPagination_pageJump")
                       (get-hickory-data paintings-uri))
             first
             (s/select (s/tag :a))
             first
             :content
             first
             (re-find #"Page \d of (\d+)")
             last
             Integer/parseInt)
        painting-links
        (fn [uri]
          (map #(-> (s/select (s/descendant
                               (s/tag :h2)
                               (s/tag :a)) %)
                    first
                    :attrs
                    :href)
               (s/select (s/id "painting-list-item")
                         (get-hickory-data uri))))
        all-painting-links
        (apply concat
               (for [page-num (map inc (range num-of-pages))]
                 (painting-links (assoc paintings-uri
                                        :path (str "/page/" page-num)))))]
    (map painting-data all-painting-links)))

#_(map :description all-painting-data)
