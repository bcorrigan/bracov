(ns bracov.core
  (:gen-class)
  (:require [clojure.xml :refer [parse]]
            [clojure.java.io :refer [input-stream]]
            [clojure.java.shell :refer [sh]]
            [clojure.string :refer [split-lines,starts-with?,replace,split]]))

;; this is hideous but it stops java puking on non-existent DTDs
(defn startparse-sax-non-validating [s ch]
  (.. (doto (. javax.xml.parsers.SAXParserFactory (newInstance))
       (.setFeature "http://apache.org/xml/features/nonvalidating/load-external-dtd" false))
       (newSAXParser) (parse s ch)))

;; Git output is parsed into this
(defrecord loc [file nr])
;; we parse jacoco xml into this
(defrecord line [loc missed covered])

;From an XML sub-object filters out only the supplied <tag> blocks
(defn filter-xml [xml tag]
  (filter #(= tag (get % :tag))
          (get xml :content)))

(defn get-attr [xml attr]
  (get-in xml [:attrs attr]))

;; extracts all the line information out of the jacoco XML
(defn lines [pkgs]
  (for [pkg pkgs
        src (filter-xml pkg :sourcefile)
        line (filter-xml src :line)
        :let [prefix (get-attr pkg :name)
              file (str prefix "/" (get-attr src :name))]]
    (->line (->loc file
                   (read-string (get-attr line :nr)))
            (read-string (get-attr line :mi))
            (read-string (get-attr line :ci)))))

;; Capture output from git diff
(defn git-lines [proj-root]
  (sh "git" "diff" "--staged" "-U0" "src/main/java/" :dir proj-root))

;;First, obtain filename by looking for line beginning with 'diff'
;;Second, for line numbers look for the 3rd field in lines beginning @@
;; eg: @@ -7 +7,3 @@ package test;
;; Here, lines 7 and 3 further lines 8,9,10 have been changed
(defn parse-git [git-diff]
  (let [lines (->> (get git-diff :out)
                   (split-lines)
                   (filter #(or (starts-with? % "diff") (starts-with? % "@@"))))
        pf-lines (reduce #(if (starts-with? %2 "diff")
                            (conj %1 (list (replace %2 #".*b/src/main/java/" "") nil))
                            (conj %1 (list (first (last %1)) (-> (split %2 #" ")
                                                                 (get 2)
                                                                 (subs 1)
                                                                 (split #",")))))
                         []
                         lines)
        rel-lines (filter #(some? (second %)) pf-lines)]
    (set (for [line rel-lines
               num (let [start (read-string (first (last line)))]
                     (range start (+ start
                                     (read-string
                                      (get-in (into [] line) [1 1] "0"))
                                     1)))]
           (->loc (first line) num)))))

; filter the jacoco lines, to only those with a matching git loc
(defn filter-lines [lines locs]
  (filter #(contains? locs (get % :loc)) lines))

;add all covered and missed
(defn cov-sum [lines]
  (let [sum (reduce #(list (+ (first %1) (get %2 :missed))
                           (+ (second %1) (get %2 :covered)))
                    '(0 0)
                    lines)]
    {:missed (first sum), :covered (second sum)}))

;; Percentage covered! => cov% = covered/(covered+missed)
(defn cov% [cov-sum]
  (let [covered (get cov-sum :covered)
        missed (get cov-sum :missed)]
    (double (* 100
               (/ covered
                  (+ covered
                     missed))))))

(defn -main
  [& args]
  (let [proj-root (first args)]
    (with-open [xin (input-stream (str proj-root "/target/site/jacoco/jacoco.xml"))]
      (let [packages (filter-xml (parse xin startparse-sax-non-validating) :package)
            git-lines (parse-git (git-lines proj-root)) ]
        (as-> (lines packages) lines
          (filter-lines lines git-lines)
          (cov-sum lines)
          (println (cov% lines)))))))

;; test stuff
;(-main "/home/bc/z/workspace/test")
