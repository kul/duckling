(ns picsou.corpus
  (:use     [picsou.time.util]) ; use grains in corpus
  (:require [clj-time.core :as t]
            [picsou.time :as time]
            [picsou.util :as util]))

(defn datetime
  "Creates a datetime condition to check if the token is valid"
  [& args] ; '2013 2 13 - 14' means "from 13 feb to 14 feb"
  (let [[date-fields [grain & other-keys-and-values]] (split-with integer? args)
        date (time/local-date-time date-fields)
        token-fields (into {:grain grain} 
                           (map vec (partition 2 other-keys-and-values)))]
    (fn
      [token context]
      (and
        (= :time (:dim token))
        (util/hash-match token-fields (:value token))
        (= (-> token :value :from) (str date))))))

(defn datetime-withzone
  "Like datetime, but also specify a timezone"
  [zone & args]
  (let [dtfn (apply datetime args)]
    (fn [token context]
      (and (dtfn token context)
           (= zone (:timezone token))))))

(defn number
  "check if the token is a number equal to value.
  If value is integer, it also checks :integer true"
  [value]
  (fn [token _] (and
                  (= :number (:dim token))
                  (or (not (integer? value)) (:integer token))
                  (= (:val token) value))))

(defn temperature
  "Create a temp condition"
  [value & [unit precision]]
  (fn [token _] (and
                  (= :temperature (:dim token))
                  (== value (-> token :val :temperature))
                  (= unit  (-> token :val :unit))
                  (= precision (-> token :val :precision)))))

(defn distance
  "Create a distance condition"
  [value & [unit precision]]
  (fn [token _] (and
                  (= :distance (:dim token))
                  (== value (-> token :val :distance))
                  (= unit  (-> token :val :unit))
                  (= precision (-> token :val :precision)))))

(defn money
  "Create a amount-of-money condition"
  [value & [unit precision]]
  (fn [token _] (and
                  (= :amount-of-money (:dim token))
                  (= value (-> token :val :amount))
                  (= unit (-> token :val :unit))
                  (= precision (-> token :val :precision)))))

(defn place
  "Create a place checker"
  [pnl n]
  (fn [token context] (and
                        (= :pnl (:dim token))
                        (= n (:n token))
                        (= pnl (:pnl token)))))

(defn metric
  "Create a metric checker"
  [cat val]
  (fn [token context] (and
                        (= :unit (:dim token))
                        (= val (:val token))
                        (= cat (:cat token)))))

(defn corpus
  "Parse corpus" ;; TODO should be able to load several files, like rules
  [forms]
  (-> (fn [state [head & more :as forms] context tests]
        (if head
          (case state
            :init (cond (map? head) (recur :test-strings more
                                      head
                                      (conj tests {:text [], :checks []}))
                    :else (throw (Exception. (str "Invalid form at init state. A map is expected for context:" (prn-str head)))))

            :test-strings (cond (string? head) (recur :test-strings more
                                                 context
                                                 (assoc-in tests
                                                   [(dec (count tests)) :text (count (:text (peek tests)))]
                                                   head))
                            (fn? head) (recur :test-checks forms
                                         context
                                         tests)
                            :else (throw (Exception. (str "Invalid form at test-strings state: " (prn-str head)))))

            :test-checks (cond (fn? head) (recur :test-checks more
                                            context
                                            (assoc-in tests
                                              [(dec (count tests)) :checks (count (:checks (peek tests)))]
                                              head))
                           (string? head) (recur :test-strings forms
                                            context
                                            (conj tests {:text [], :checks []}))
                           :else (throw (Exception. (str "Invalid form at test-checks stats:" (prn-str head))))))
          {:context context, :tests tests}))
    (apply [:init forms [] []])))

(defmacro this-ns "Total hack to get ns of this file at compile time" [] *ns*)

(defn read-corpus
  "Reade a list of symbol and return a Corpus map {:context {}, :tests []}"
  [new-file]
  (let [symbols (read-string (slurp new-file))]
    (corpus (map #(binding [*ns* (this-ns)] (eval %)) symbols))))
