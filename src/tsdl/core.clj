(ns tsdl.core
  (:require [tsdl.downloader :refer [download-all-paintings]))

(set! *warn-on-reflection* true)

(defn -main [& args]
  (download-all-paintings))
