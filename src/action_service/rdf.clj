(ns action-service.rdf
  (:require [clojure.string :as string]))


(defn- md5
  "Generate a MD5 hash from an input string"
  [s]
  (apply str
         (map (partial format "%02x")
              (.digest (doto (java.security.MessageDigest/getInstance "MD5")
                         .reset
                         (.update (.getBytes s)))))))

(defn- escape
  "Escape data literal for the Turtle data format."
  [s]
  (-> s
      str
      (string/replace #"\"" "\\\\\"")))


(defn keyword-to-rdf [nspcs k]
  (let [[k1 k2] (clojure.string/split (name k) #":")]
    (str "<" (nspcs (keyword k1)) k2 "> ")))


(defn rdf-item-to-ttl
  [ns item]
  (if (keyword? item) (keyword-to-rdf ns item) item ))

(defn rdf1subj2ttl
  "Generates RDF/turtle from vector that same subject triples"
  [ns subject & rest]
  (if (empty? rest)
    ""
    (str
      (rdf-item-to-ttl ns subject) " "
      (rdf-item-to-ttl ns (first rest)) " "
      (rdf-item-to-ttl ns (second rest)) ".\n"
      (apply rdf1subj2ttl ns subject (drop 2 rest)))))

(defn rdf
  "Generate RDF/Turtle from vector list where each vector represents triples for same subject.
  First parameter is namespaces.  From it creates namespaces with prefixes"
  [ns & rest]
  (clojure.string/join (map #(apply rdf1subj2ttl ns %) rest)) )




(def turtle "Alias for the (rdf) function" rdf)


(defn variable?
  [v]
  (= (first (str v)) \?))

(defn iri
  "Serialize a URI where you provide the full URI as a string"
  [uri]
  (str "<" uri "> "))

(defn var [v]
  (str v)
  )

(defn a
  "Specify the rdf:type of an entity being described"
  []
  (str " <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> "))

(defn literal
  "[v] String that represents the literal value to create.
   [lang] (optional) Keyword referring to the ISO 639-1 language code
   [type] (optional) Namespace function or string that refers to a
                     datatype to specify for the literal value

   Usages:
     ; specify a simple literal
     (literal \"bob\")

     ; specify the language of the literal
     (literal \"bob\" :lang :en)

     ; if the namespace 'xsd' has been defined
     (literal \"bob\" :type (xsd :string))

     ; if you want to use an abreviated URI you can do this that way
     (literal \"bob\" :type \"xsd:string\")"
  [v & {:keys [lang type]
        :or   {lang nil
               type nil}}]
  (apply str
         (str "\"\"\"" (if (keyword? v)
                         (escape (name v))
                         (escape v))"\"\"\"")
         (if lang
           (str "@" (name lang))
           (when type
             (str "^^" (name type))))))





(defn gen-id
  [ns entity & keys]
  (iri (str ns entity "/" (string/join "_" (->>
                                             (map string/trim keys)
                                             (map #(string/replace % "<" "" ))
                                             (map #(string/replace % ">" "" ))
                                             (map #(string/replace % ns "" ))
                                             )  ))
       ;(eval (prefix (str entity "/" (string/join "_" keys ))))
       ))

(defn now
  []
  (java.time.ZonedDateTime/now (java.time.ZoneId/of "UTC"))
  )

(defn add-days
  [date  days-to-add]
  (if (not (nil? date)) (.plusDays date days-to-add))
  )

(defn dateTime-to-date
  [v]
  (first (string/split (str v) #"T"))
  )

(defn to-rdftype
  "docstring"
  [v]
  (cond
    (= (type v) java.time.LocalDateTime) (literal v :type "<http://www.w3.org/2001/XMLSchema#dateTime>")
    (= (type v) java.time.ZonedDateTime) (literal (.format v (java.time.format.DateTimeFormatter/ISO_INSTANT)) :type "<http://www.w3.org/2001/XMLSchema#dateTime>")
    (= (type v) java.lang.String) (literal v :type "<http://www.w3.org/2001/XMLSchema#string>")
    :else v ))




(defn process-statement
  [statement]
  (cond
    (seq? statement )   (str statement)                                    ;nepabaigta
    (vector? statement) (str statement)
    (variable? statement) (str statement)
    (string? statement) statement
    (clojure.test/function? statement) (to-rdftype (eval (conj () statement)))
    (symbol? statement) (str (to-rdftype (eval statement)))
    :else (str statement)
    )
  )


(defn filter-s
  [rest]
  (let [statements rest]
    (cond
      (= (first statements) 'and)
      (string/join " && " (map #(str "(" % ") ") ( map #(filter-s %1) (into () (next statements)) )))
      (= (first statements) '<)
      (str (process-statement (nth statements 1)) " < " (process-statement (nth statements 2)))
      (= (first statements) '>)
      (str (process-statement (nth statements 1)) " > " (process-statement (nth statements 2)))
      :else (str statements)
      )
    )
  )

; old code
(comment defmacro defns
         "Create a new namespace that can be used to create the clj-turtle triples"
         [prefix namespace]
         (let [entity (gensym "entity-")
               body (gensym "body-")]
           `(defn ~prefix
              [~entity & ~body]
              (str "<" ~namespace (name ~entity) "> " (apply str (rest ~body))))))


(comment "we don't need this anymore" defn rei
         "Reify a clj-turtle triple"
         [& rest]
         (loop [forms rest
                statements []]
           (if-not (empty? forms)
             (recur (drop 2 forms)
                    (into statements [(apply str (nth forms 0) (nth forms 1))]))
             statements)))