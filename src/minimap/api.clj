(ns minimap.api
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(s/def ::float-str #(re-matches #"-?\d+(\.\d+)?" %))
(s/def ::int-str #(re-matches #"\d+" %))

(s/def ::tile-provider string?)
(s/def ::lat (s/and ::float-str #(<= -90 (Float/parseFloat %) 90)))
(s/def ::lng (s/and ::float-str #(<= -180 (Float/parseFloat %) 190)))
(s/def ::ne-lat ::lat)
(s/def ::sw-lat ::lat)
(s/def ::ne-lng ::lng)
(s/def ::sw-lng ::lng)
(s/def ::zoom ::int-str)
(s/def ::circle #(re-matches #"\d+(,#?[a-z0-f]+)?" %))

(s/def ::map-request
  (s/keys :req-un [::tile-provider ::lat ::lng]
          :opt-un [::zoom ::circle]))

(s/def ::bounds-request
  (s/keys :req-un [::tile-provider ::sw-lat ::sw-lng ::ne-lat ::ne-lng]
          :opt-un [::clip ::circle]))


(defn explain-pred [pred]
  (case (first pred)
    contains? (str "missing parameter: " (name (last pred)))

    (or and) (str/join (str " " (str/upper-case (str (first pred))) " ")
                       (map #(str "(" (explain-pred %) ")") (rest pred)))

    (re-matches re-find) (str "doesn't match pattern: " (second pred))

    ;; Default:
    (str "doesn't match predicate: " pred)))

(defn explain-problem [{:keys [path pred]}]
  (str (when path
         (str (str/join "." (map name path)) " "))

       (explain-pred pred)))

(defn error-message [expl]
  (map explain-problem (::s/problems expl)))

(def converter
  {::float-str #(Float/parseFloat %)
   ::int-str #(Integer/parseInt %)})

(defn convert
  ([arg spec converters]
   (let [desc (s/describe spec)
         conv (converters spec)]
     (cond conv (conv arg)

           (symbol? arg) arg

           (#{'or 'and} (first desc))
           (first (remove nil? (map #(try (convert arg % converters)
                                          (catch Exception _ nil))
                                    (rest desc))))

           (= (first desc) 'keys)
           (into {} (map (fn [[k kspec]]
                           [k (some-> (get arg k)
                                      (convert kspec converters))])

                         (mapcat (fn [[ktype ks]]
                                   (map vector
                                        (if (#{:req-un :opt-un} ktype)
                                          (map #(keyword (name %)) ks)
                                          ks)
                                        ks))
                                 (partition 2 (rest desc))))))))
  ([arg spec]
   (convert arg spec converter)))

(defn api-endpoint [spec f]
  (fn [{:keys [params] :as req}]
    (let [conformed (s/conform spec params)]
      (if (= conformed ::s/invalid)
        ;; Respond with a user error if the request params do not conform to the
        ;; API specification.
        {:status 400
         :headers {"Content-type" "application/json"}
         :body (json/write-str {:errors (error-message (s/explain-data spec params))
                                :request-params params})}

        (f (assoc req :conformed conformed))))))

(defmacro defapi [name spec params & body]
  `(def ~name (api-endpoint ~spec (fn ~name ~params ~@body))))
