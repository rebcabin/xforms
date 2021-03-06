(ns net.cgrand.xforms.rfs
  {:author "Christophe Grand"}
  (:refer-clojure :exclude [str last min max])
  #?(:cljs (:require-macros
             [net.cgrand.macrovich :as macros]
             [net.cgrand.xforms.rfs :refer [or-instance?]])
      :clj (:require [net.cgrand.macrovich :as macros]))
  (:require [#?(:clj clojure.core :cljs cljs.core) :as core])
  #?(:cljs (:import [goog.string StringBuffer])))

(macros/deftime
  (defmacro ^:private or-instance? [class x y]
    (let [xsym (gensym 'x_)]
      `(let [~xsym ~x]
         (if (instance? ~class ~xsym) ~(with-meta xsym {:tag class}) ~y)))))

(declare str!)

(macros/usetime

#? (:cljs
     (defn ^:private cmp [f a b]
       (let [r (f a b)]
         (cond
           (number? r) r
           r -1
           (f b a) 1
           :else 0))))
  
(defn minimum
 ([comparator]
   (minimum comparator nil))
 ([#?(:clj ^java.util.Comparator comparator :cljs comparator) absolute-maximum]
   (fn
     ([] ::+∞)
     ([x] (if (#?(:clj identical? :cljs keyword-identical?) ::+∞ x)
            absolute-maximum
            x))
     ([a b] (if (or (#?(:clj identical? :cljs keyword-identical?) ::+∞ a) (pos? (#?(:clj .compare :cljs cmp) comparator a b))) b a)))))

(defn maximum
  ([comparator]
    (maximum comparator nil))
  ([#?(:clj ^java.util.Comparator comparator :cljs comparator) absolute-minimum]
    (fn
      ([] ::-∞)
      ([x] (if (#?(:clj identical? :cljs keyword-identical?) ::-∞ x)
             absolute-minimum
             x))
      ([a b] (if (or (#?(:clj identical? :cljs keyword-identical?) ::-∞ a) (neg? (#?(:clj .compare :cljs cmp) comparator a b))) b a)))))

(def min (minimum compare))

(def max (maximum compare))

(defn avg
  "Reducing fn to compute the arithmetic mean."
  ([] (transient [0 0]))
  ([[n sum]] (/ sum n))
  ([acc x] (avg acc x 1))
  ([[n sum :as acc] x w] ; weighted mean
    (-> acc (assoc! 0 (+ n w)) (assoc! 1 (+ sum (* w x))))))

(defn sd
  "Reducing fn to compute the standard deviation. Returns 0 if no or only one item."
  ([] #?(:clj (double-array 3) :cljs #js [0.0 0.0 0.0]))
  ([^doubles a]
    (let [s (aget a 0) s2 (aget a 1) n (aget a 2)]
      (if (< 1 n)
        (Math/sqrt (/ (- s2 (/ (* s s) n)) (dec n)))
        0.0)))
  ([^doubles a x]
    (let [s (aget a 0) s2 (aget a 1) n (aget a 2)]
      (doto a
        (aset 0 (+ s x))
        (aset 1 (+ s2 (* x x)))
        (aset 2 (inc n))))))

(defn last
  "Reducing function that returns the last value."
  ([] nil)
  ([x] x)
  ([_ x] x))

(defn str!
  "Like xforms/str but returns a StringBuilder."
  ([] (#?(:clj StringBuilder. :cljs StringBuffer.)))
  ([sb] (or-instance? #?(:clj StringBuilder :cljs StringBuffer) sb (#?(:clj StringBuilder. :cljs StringBuffer.) (core/str sb)))) ; the instance? checks are for compatibility with str in case of seeded reduce/transduce.
  ([sb x] (.append (or-instance? #?(:clj StringBuilder :cljs StringBuffer) sb (#?(:clj StringBuilder. :cljs StringBuffer.) (core/str sb))) x)))

(def str
  "Reducing function to build strings in linear time. Acts as replacement for clojure.core/str in a reduce/transduce call."
  (completing str! core/str))

#_(defn juxt
   "Returns a reducing fn which compute all rfns at once and whose final return
   value is a vector of the final return values of each rfns."
   [& rfns]
   (let [rfns (mapv ensure-kvrf rfns)]
     (kvrf
       ([] (mapv #(vector % (volatile! (%))) rfns))
       ([acc] (mapv (fn [[rf vacc]] (rf (unreduced @vacc))) acc))
       ([acc x]
         (let [some-unreduced (core/reduce (fn [some-unreduced [rf vacc]] 
                                            (when-not (reduced? @vacc) (vswap! vacc rf x) true))
                                false acc)]
           (if some-unreduced acc (reduced acc))))
       ([acc k v]
         (let [some-unreduced (core/reduce (fn [some-unreduced [rf vacc]] 
                                            (when-not (reduced? @vacc) (vswap! vacc rf k v) true))
                                false acc)]
           (if some-unreduced acc (reduced acc)))))))

#_(defn juxt-map
   [& key-rfns]
   (let [f (apply juxt (take-nth 2 (next key-rfns)))
         keys (vec (take-nth 2 key-rfns))]
     (let [f (ensure-kvrf f)]
       (kvrf
         ([] (f))
         ([acc] (zipmap keys (f acc)))
         ([acc x] (f acc x))
         ([acc k v] (f acc k v))))))
)
