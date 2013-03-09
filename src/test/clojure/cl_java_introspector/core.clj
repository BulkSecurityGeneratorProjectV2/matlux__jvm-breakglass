(ns cl-java-introspector.core)

(require '[clojure.tools.nrepl :as repl])
(require '[clojure.walk :only [walk prewalk postwalk]])

(import '(net.matlux NreplServerStartup))
(import '(net.matlux NreplServerWithSpringLog4jStartup))
(import '(java.lang.reflect Modifier))




(comment
  (let [department (. NreplServerStartup/instance getObj "department")
        fields (map #(do (.setAccessible % true) %) (into [] (. (. department getClass) getDeclaredFields)))]
    fields)

(def department (. NreplServerStartup/instance getObj "department"))

)


(defn member-field? [field] (not (Modifier/isStatic (.getModifiers field))))
(defn get-member-fields [obj] (map #(vector (.getName %) (.get % obj)) (filter member-field? (map #(do (.setAccessible % true) %) (into [] (. (. obj getClass) getDeclaredFields))))))
(def primitive? (some-fn string? number?))
(def clojure-struct? (some-fn map? set? vector? list?))

(defn to-map [obj]
  (cond
   ((some-fn nil? primitive? clojure-struct?) obj) obj
   (instance? java.lang.Iterable obj) (into [] obj)
   (instance? java.util.Map obj) (reduce #(assoc %1 %2 ((into {} obj) %2)) {} (keys m))
   :else (reduce #(let [[fname ob] %2] (assoc %1 fname ob )) {} (get-member-fields obj))
   ))

(def to-tree (partial clojure.walk/prewalk to-map))


;(to-tree to-map NreplServerStartup/instance)
;(obj2map NreplServerStartup/instance 50)

(defn get-obj-methods [obj]
  (let [obj2methods (fn [obj] (map #(do (.setAccessible % true) %) (into [] (. (. obj getClass) getDeclaredMethods))))
        get-inst-methods (fn [fields] (filter #(not (Modifier/isStatic (.getModifiers %))) fields))
        method2ref (fn [field obj] (.get field obj))
        ]

    (obj2methods obj)))
;(get-obj-methods "")

(defn obj2map [obj level]
  (if (zero? level) obj (let [obj2fields (fn [obj] (map #(do (.setAccessible % true) %) (into [] (. (. obj getClass) getDeclaredFields))))
         get-inst-fields (fn [fields] (filter #(not (Modifier/isStatic (.getModifiers %))) fields))
         field2ref (fn [field obj] (.get field obj))
         ]
                                        ;(reduce #(assoc %1 (.getName %2) (field2value %2)) {} in-fields)
     (cond (nil? obj) nil
           (instance? java.lang.String obj) obj
           (instance? java.lang.Number obj) obj
           (instance? java.lang.Iterable obj) (into [] (map (fn [e] (obj2map e (dec level))) (into [] obj)))
           (instance? java.util.Map obj) (let [m (into {} obj)
                                               ks (keys m)
                                               ]
                                           (reduce #(assoc %1 %2 (obj2map (m %2) (dec level))) {} ks))
           :else (reduce #(assoc %1 (.getName %2) (obj2map (field2ref %2 obj) (dec level))) {} (get-inst-fields (obj2fields obj))) )
     )))



(defn remote-execute [hostname port code]
  (try
    (with-open [conn (repl/connect :host hostname :port port)]
     (-> (repl/client conn 1000)
       (repl/message {:op :eval :code code})
       repl/response-values))
     (catch java.net.ConnectException e
         ;(println "Caught" (.getMessage e))
         "cannot connect")
     (finally
          ;(println "")
          )))


(def code2inject
  "(import '(net.matlux NreplServerStartup))
   (import '(java.lang.reflect Modifier))
   (import '(net.matlux NreplServerWithSpringLog4jStartup))
   (defn obj2map [obj]
   (let [obj2fields (fn [obj] (map #(do (.setAccessible % true) %) (into [] (. (. obj getClass) getDeclaredFields))))
        get-inst-fields (fn [fields] (filter #(not (Modifier/isStatic (.getModifiers %))) fields))
        field2ref (fn [field obj] (.get field obj))
        ]
                                        ;(reduce #(assoc %1 (.getName %2) (field2value %2)) {} in-fields)
    (cond (nil? obj) nil
          (instance? java.lang.String obj) obj
          (instance? java.lang.Number obj) obj
          (instance? java.lang.Iterable obj) (into [] (map (fn [e] (obj2map e)) (into [] obj)))
          (instance? java.util.Map obj) (let [m (into {} obj)
                                              ks (keys m)
                                              ]
                                          (reduce #(assoc %1 %2 (obj2map (m %2))) {} ks))
          :else (reduce #(assoc %1 (.getName %2) (obj2map (field2ref %2 obj))) {} (get-inst-fields (obj2fields obj))) )
    ))")

(def code2execute
  "(obj2map NreplServerStartup/instance)
   (into [] (.getBeanDefinitionNames (.getApplicationContext NreplServerWithSpringLog4jStartup/instance)))
   (obj2map (.getObj NreplServerWithSpringLog4jStartup/instance \"department\"))
   (obj2map (.getObj NreplServerWithSpringLog4jStartup/instance \"employee1\"))
   (obj2map nil)")


(defn -main []

  (println "introspecting objects:")
  (remote-execute "localhost" 1112 code2inject)
  (println (remote-execute "localhost" 1112 code2execute))
  (System/exit 0)
  )






(comment
  (obj2map department)
  (obj2map NreplServerStartup/instance)
  (obj2map NreplServerWithSpringLog4jStartup/instance)
  (obj2map nil)
  (obj2map (.getObj NreplServerWithSpringLog4jStartup/instance "department"))

  (println (remote-execute "localhost" 1112 code2execute2))

)