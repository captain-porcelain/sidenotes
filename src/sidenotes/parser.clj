;; This file contains the complete Marginalia parser.
;; It leverages the Clojure reader instead of implementing a complete
;; Clojure parsing solution.
;;
;; And seems to be in need of some more documentation and cleanup for
;; readability.
(ns sidenotes.parser
  "Provides the parsing facilities for Marginalia."
  (:require
    [clojure.string :as string]
    [clojure.java.io :as io]
    [clojure.tools.namespace :as ctn]
    [cljs.tagged-literals :as ctl]))

;; ====================================================================================================
;; Helper functions

;; Extracted from clojure.contrib.reflect
(defn get-field
  "Access to private or protected field.  field-name is a symbol or
  keyword."
  [klass field-name obj]
  (-> klass (.getDeclaredField (name field-name))
      (doto (.setAccessible true))
      (.get obj)))

;; Extracted from clojure.contrib.reflect
(defn call-method
  "Calls a private or protected method.

  params is a vector of classes which correspond to the arguments to
  the method e

  obj is nil for static methods, the instance object otherwise.

  The method-name is given a symbol or a keyword (something Named)."
  [klass method-name params obj & args]
  (-> klass (.getDeclaredMethod (name method-name)
                                (into-array Class params))
      (doto (.setAccessible true))
      (.invoke obj (into-array Object args))))


;; ====================================================================================================
;; Comment Handling

;; A simple record for holding comments.
(defrecord Comment [content])

;; Attach a method for printing to Comment records.
(defmethod print-method Comment [comment ^String out]
  (.write out (str \" (.content comment) \")))

;; Hold comments that are defined at top level.
(def top-level-comments (atom []))

;; Hold comments that are defined inside a form.
(def sub-level-comments (atom []))

(def ^{:dynamic true} *comments* nil)
(def ^{:dynamic true} *lift-inline-comments* nil)
(def ^{:dynamic true} *delete-lifted-comments* nil)

(def comments-enabled?
  "Remeber if adding comments is currently enabled or disabled."
  (atom true))

(def directives
  "Marginalia can be given directives in comments.  A directive is a comment
  line containing a directive name, in the form `;; @DirectiveName`.
  Directives change the behavior of the parser within the files that contain
  them.

  The following directives are defined:

  * `@MSidenotesDisable` suppresses subsequent comments from the docs
  * `@MSidenotesEnable` includes subsequent comments in the docs"
  {"SidenotesDisable" (fn [] (swap! comments-enabled? false))
   "SidenotesEnable"  (fn [] (swap! comments-enabled? true))})

(defn process-directive!
  "If the given line is a directive, applies it.  Returns a value
  indicating whether the line should be included in the comments
  list."
  [line]
  (let [directive (->> (re-find #"^;+\s*@(\w+)" line)
                       (last)
                       (get directives))]
    (when directive
      (directive))
    (not directive)))

(defn read-comment
  "Read a comment from the reader."
  ([reader semicolon]
   (let [sb (StringBuilder.)]
     (.append sb semicolon)
     (loop [c (.read reader)]
       (let [ch (char c)]
         (if (or (= ch \newline)
                 (= ch \return))
           (let [line (dec (.getLineNumber reader))
                 text (.toString sb)
                 include? (process-directive! text)]
             (when (and include? @comments-enabled?)
               (swap! *comments* conj {:form (Comment. text)
                                       :text [text]
                                       :start line
                                       :end line}))
             reader)
           (do
             (.append sb (Character/toString ch))
             (recur (.read reader))))))))
  ([reader semicolon opts pending]
   (read-comment reader semicolon)))

(defn set-comment-reader
  "Set the given reader as handler for lines starting with ;"
  [reader]
  (aset (get-field clojure.lang.LispReader :macros nil)
        (int \;)
        reader))

(defn skip-spaces-and-comments
  "Skip forward until something besides a comment or whitespace shows up."
  [rdr]
  (loop [c (.read rdr)]
    (cond (= c -1) nil
          (= (char c) \;) (do (read-comment rdr \;)
                              (recur (.read rdr)))
          (#{\space \tab \return \newline \,} (char c)) (recur (.read rdr))
          :else (.unread rdr c))))


;; ====================================================================================================
;; Keyword Handling

(defrecord DoubleColonKeyword [content])

(defmethod print-method DoubleColonKeyword [dck ^java.io.Writer out]
  (.write out (str \: (.content dck))))

(defmethod print-dup DoubleColonKeyword [dck ^java.io.Writer out]
  (print-method dck out))

(defn ^:private read-token [reader c]
  (call-method clojure.lang.LispReader :readToken
               [java.io.PushbackReader Character/TYPE]
               nil reader c))

;;; Clojure 1.9 changed the signature of LispReader/matchSymbol, taking a new
;;; parameter of type LispReader$Resolver.  Conveniently, we can test for the
;;; existence of the *reader-resolver* var to detect running under 1.9.
;;;
;;; We must take care to use the correct overload for the project's runtime,
;;; else we will crash and fail people's builds.
(if-let [resolver-var (resolve '*reader-resolver*)]
  (defn ^:private match-symbol [s]
    (call-method clojure.lang.LispReader :matchSymbol
                 [String, (Class/forName "clojure.lang.LispReader$Resolver")]
                 nil s (deref resolver-var)))
  (defn ^:private match-symbol [s]
    (call-method clojure.lang.LispReader :matchSymbol
                 [String]
                 nil s)))

(defn read-keyword
  "Read a keyword from reader."
  ([reader colon]
   (let [c (.read reader)]
     (if (= (int \:) c)
       (-> (read-token reader (char c))
           match-symbol
           DoubleColonKeyword.)
       (do (.unread reader c)
           (-> (read-token reader colon)
               match-symbol)))))
  ([reader colon opts pending]
   (read-keyword reader colon)))

(defn set-keyword-reader
  "Set the given reader as handler for keywords."
  [reader]
  (aset (get-field clojure.lang.LispReader :macros nil)
        (int \:)
        reader))


;; ====================================================================================================
;; Handling of parsed forms

(defn adjacent?
  "Check if two sections are adjacent."
  [f s]
  (= (-> f :end) (-> s :start dec)))

(defn- ->str
  "Convert a section to a string."
  [m]
  (-> (-> m :form .content)
      (string/replace #"^;+\s(\s*)" "$1")
      (string/replace #"^;+" "")))

(defn merge-comments
  "Merge two comment sections into one."
  [f s]
  {:form (Comment. (str (->str f) "\n" (->str s)))
   :text (into (:text f) (:text s))
   :start (:start f)
   :end (:end s)})

(defn parse-form
  "Parse one form. Throw exception with start line number included when an error is encountered."
  [reader start]
  (binding [*comments* sub-level-comments]
    (try (. clojure.lang.LispReader
            (read reader {:read-cond :preserve
                           :eof :_eof}))
         (catch Exception ex
           (let [msg (str "Problem parsing near line " start
                          " <" (.readLine reader) ">"
                          " original reported cause is "
                          (.getCause ex) " -- "
                          (.getMessage ex))
                 e (RuntimeException. msg)]
             (.setStackTrace e (.getStackTrace ex))
             (throw e))))))

(def paragraph-comment
  "An empty comment that can be injected between other comments."
  {:form (Comment. ";;") :text [";;"]})

;; We optionally lift inline comments to the top of the form.
;; This monstrosity ensures that each consecutive group of inline
;; comments is treated as a mergable block, but with a fake
;; blank comment between non-adjacent inline comments. When merged
;; and converted to markdown, this will produce a paragraph for
;; each separate block of inline comments.
(defn merge-inline-comments
  "Merge adjacent comments together"
  [cs c]
  (if (re-find #"^;(\s|$)" (.content (:form c)))
    cs
    (if-let [t (peek cs)]
      (if (adjacent? t c)
        (conj cs c)
        (conj cs paragraph-comment c))
      (conj cs c))))

(defn parse-inline-comments
  "Parse all inline comments and merge them if appropriate."
  [start]
  (when (and *lift-inline-comments*
             (seq @sub-level-comments))
    (cond->> (reduce merge-inline-comments
                     []
                     @sub-level-comments)
      (seq @top-level-comments)
      (into [paragraph-comment])
      true
      (mapv #(assoc % :start start :end (dec start))))))

(defn parse*
  "Parse all contents in reader."
  [reader]
  (take-while
    #(not= :_eof (:form %))
    (flatten
      (repeatedly
        (fn []
          (binding [*comments* top-level-comments]
            (skip-spaces-and-comments reader))
          (let [start (.getLineNumber reader)
                form (parse-form reader start)
                end (.getLineNumber reader)
                code {:form form :start start :end end}
                inline-comments (parse-inline-comments start)
                comments (concat @top-level-comments inline-comments)]
            (swap! top-level-comments (constantly []))
            (swap! sub-level-comments (constantly []))
            (if (empty? comments)
              [code]
              (vec (concat comments [code])))))))))


;; ====================================================================================================
;; Getting all documentation strings

(defn strip-docstring
  "Remove the docstring from a form."
  [docstring raw]
  (-> raw
      (string/replace (str \" (-> docstring
                                  str
                                  (string/replace "\"" "\\\""))
                           \")
                      "")
      (string/replace #"#?\^\{\s*:doc\s*\}" "")
      (string/replace #"\n\s*\n" "\n")
      (string/replace #"\n\s*\)" ")")))

(defn get-var-docstring
  [nspace-sym sym]
  (let [s (if nspace-sym
            (symbol (str nspace-sym) (str sym))
            (symbol (str sym)))]
    (try
      (-> `(var ~s) eval meta :doc)
      ;; HACK: to handle types
      (catch Exception _))))

(defn- extract-common-docstring
  [form raw nspace-sym]
  (let [sym (second form)]
    (if (symbol? sym)
      (let [maybe-metadocstring (:doc (meta sym))]
        (let [nspace (find-ns sym)
              [maybe-ds remainder] (let [[_ _ ? & more?] form] [? more?])
              docstring (if (and (string? maybe-ds) remainder)
                          maybe-ds
                          (if (= (first form) 'ns)
                            (if (not maybe-metadocstring)
                              (when (string? maybe-ds) maybe-ds)
                              maybe-metadocstring)
                            (if-let [ds maybe-metadocstring]
                              ds
                              (when nspace
                                (-> nspace meta :doc)
                                (get-var-docstring nspace-sym sym)))))]
          [(when docstring
             ;; Exclude flush left docstrings from adjustment:
             (if (re-find #"\n[^\s]" docstring)
               docstring
               (string/replace docstring #"\n  " "\n")))
           (strip-docstring docstring raw)
           (if (or (= 'ns (first form)) nspace) sym nspace-sym)]))
      [nil raw nspace-sym])))

(defn- extract-impl-docstring
  [fn-body]
  (filter string? (rest fn-body)))

(defn- extract-internal-docstrings
  [body]
  (mapcat #(extract-impl-docstring %)
          body))


(defmulti dispatch-form
  (fn [form _ _]
    (if (seq? form) (first form) form)))

(defmethod dispatch-form 'defprotocol
  [form raw nspace-sym]
  (let [[ds r s] (extract-common-docstring form raw nspace-sym)]
    (let [internal-dses (if ds
                          (extract-internal-docstrings (nthnext form 3))
                          (extract-internal-docstrings (nthnext form 2)))]
      (with-meta
        [ds r s]
        {:internal-docstrings internal-dses}))))

(defmethod dispatch-form 'ns
  [form raw nspace-sym]
  (extract-common-docstring form raw nspace-sym))

(defmethod dispatch-form 'def
  [form raw nspace-sym]
  (extract-common-docstring form raw nspace-sym))

(defmethod dispatch-form 'defn
  [form raw nspace-sym]
  (extract-common-docstring form raw nspace-sym))

(defmethod dispatch-form 'defn-
  [form raw nspace-sym]
  (extract-common-docstring form raw nspace-sym))

(defmethod dispatch-form 'defmulti
  [form raw nspace-sym]
  (extract-common-docstring form raw nspace-sym))

(defmethod dispatch-form 'defmethod
  [form raw nspace-sym]
  [nil raw nspace-sym])

(defn dispatch-inner-form
  [form raw nspace-sym]
  (conj
    (reduce (fn [[adoc araw] inner-form]
              (if (seq? inner-form)
                (let [[d r] (dispatch-form inner-form
                                           araw
                                           nspace-sym)]
                  [(str adoc d) r])
                [adoc araw]))
            [nil raw]
            form)
    nspace-sym))

(defn- dispatch-literal
  [form raw nspace-sym]
  [nil raw])

(defn- literal-form? [form]
  (or (string? form) (number? form) (keyword? form) (symbol? form)
      (char? form) (true? form) (false? form) (instance? java.util.regex.Pattern form)))

(defmethod dispatch-form :default
  [form raw nspace-sym]
  (cond (literal-form? form)
        (dispatch-literal form raw nspace-sym)

        (and (first form)
             (.isInstance clojure.lang.Named (first form))
             (re-find #"^def" (-> form first name)))
        (extract-common-docstring form raw nspace-sym)

        :else
        (dispatch-inner-form form raw nspace-sym)))

(defn unpack-reader-conditional
  "If the given form is a reader conditional return the inner form."
  [form]
  (if (instance? clojure.lang.ReaderConditional form)
    (.form form)
    form))

(defn extract-docstring [m raw nspace-sym]
  (let [raw (string/join "\n" (subvec raw (-> m :start dec) (:end m)))
        form (unpack-reader-conditional (:form m))]
    (dispatch-form form raw nspace-sym)))


;; ====================================================================================================
;; Preparing results for output

(defn comment?
  "Check if o is a comment."
  [o]
  (->> o :form (instance? Comment)))

(defn code?
  "Check if o is code."
  [o]
  (and (->> o :form (instance? Comment) not)
       (->> o :form nil? not)))

(defn arrange-in-sections
  [parsed-code raw-code]
  (loop [sections []
         f (first parsed-code)
         s (second parsed-code)
         nn (nnext parsed-code)
         nspace nil]
    (if f
      (cond
        ;; ignore comments with only one semicolon
        (and (comment? f) (re-find #"^;(\s|$)" (-> f :form .content)))
        (recur sections s (first nn) (next nn) nspace)

        ;; merging comments block
        (and (comment? f) (comment? s) (adjacent? f s))
        (recur sections (merge-comments f s)
               (first nn) (next nn)
               nspace)

        ;; merging adjacent code blocks
        (and (code? f) (code? s) (adjacent? f s))
        (let [[fdoc fcode nspace] (extract-docstring f raw-code nspace)
              [sdoc scode _] (extract-docstring s raw-code nspace)]
          (recur sections (assoc s
                                 :type :code
                                 :raw (str (or (:raw f) fcode) "\n" scode)
                                 :docstring (str (or (:docstring f) fdoc) "\n\n" sdoc))
                 (first nn) (next nn) nspace))

        ;; adjacent comments are added as extra documentation to code block
        (and (comment? f) (code? s) (adjacent? f s))
        (let [[doc code nspace] (extract-docstring s raw-code nspace)]
          (recur sections (assoc s
                                 :type :code
                                 :raw (if *delete-lifted-comments*
                                        ;; this is far from perfect but should work
                                        ;; for most cases: erase matching comments
                                        ;; and then remove lines that are blank
                                        (-> (reduce (fn [raw comment]
                                                      (string/replace raw
                                                                      (str comment "\n")
                                                                      "\n"))
                                                    code
                                                    (:text f))
                                            (string/replace #"\n\s+\n" "\n"))
                                        code)
                                 :docstring (str doc "\n\n" (->str f)))
                 (first nn) (next nn) nspace))

        ;; adding comment section
        (comment? f)
        (recur (conj sections (assoc f :type :comment :raw (->str f)))
               s
               (first nn) (next nn)
               nspace)

        ;; adding code section
        :else
        (let [[doc code nspace] (extract-docstring f raw-code nspace)]
          (recur (conj sections (if (= (:type f) :code)
                                  f
                                  {:type :code
                                   :raw code
                                   :docstring doc}))
                 s (first nn) (next nn) nspace)))
      sections)))


;; ====================================================================================================
;; Data Onboarding Helpers

(defn buffered-string-reader
  "Make a buffered string reader for given string."
  [source-string]
  (java.io.BufferedReader.
    (java.io.StringReader.
      (str source-string "\n"))))

(defn read-lines
  "Read the string into vector of lines."
  [source-string]
  (vec (line-seq (buffered-string-reader source-string))))

(defn line-numbering-reader
  "Create a line numbering reader for given string."
  [source-string]
  (clojure.lang.LineNumberingPushbackReader. (buffered-string-reader source-string)))

(defn parse
  "Handle setting all readers and parse the given source."
  [source-string]
  (let [reader (line-numbering-reader source-string)
        old-cmt-rdr (aget (get-field clojure.lang.LispReader :macros nil) (int \;))]
    (try
      (set-comment-reader read-comment)
      (set-keyword-reader read-keyword)
      (let [parsed-code (-> reader parse* doall)]
        (set-comment-reader old-cmt-rdr)
        (set-keyword-reader nil)
        parsed-code)
      (catch Exception e
        (set-comment-reader old-cmt-rdr)
        (set-keyword-reader nil)
        (throw e)))))

(defn parse-into-sections
  "Parse the given source and prepare sections."
  [source-string]
  (arrange-in-sections (parse source-string) (read-lines source-string)))

(defn cljs-file?
  "Check if a file ends with cljs"
  [filepath]
  (.endsWith (string/lower-case filepath) "cljs"))

(defn cljx-file?
  "Check if a file ends with cljx"
  [filepath]
  (.endsWith (string/lower-case filepath) "cljx"))

(defn cljc-file?
  "Check if a file ends with cljc"
  [filepath]
  (.endsWith (string/lower-case filepath) "cljc"))

(def cljx-data-readers {'+clj identity
                        '+cljc identity
                        '+cljs identity})

(defmacro with-readers-for
  "Bind the data readers to ones that match the file name and execute the body."
  [file & body]
  `(let [readers# (merge {}
                         (when (cljs-file? ~file) ctl/*cljs-data-readers*)
                         (when (cljx-file? ~file) cljx-data-readers)
                         (when (cljc-file? ~file) cljx-data-readers)
                         default-data-readers)]
     (binding [*data-readers* readers#]
       ~@body)))


;; ====================================================================================================
;; Parsing entry points

(defn parse-file
  "Parse the given file into a list of forms."
  [filename]
  (with-readers-for filename
    (parse-into-sections (slurp filename))))

(defn parse-ns
  "Get the namespace from a file."
  [filename]
  (let [file (io/file filename)
        filename (.getName file)]
    (with-readers-for filename
      (or (not-empty (-> file
                         (ctn/read-file-ns-decl)
                         (second)
                         (str)))
          filename))))

