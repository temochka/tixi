(ns tixi.drawer
  (:require-macros [schema.macros :as scm]
                   [tixi.utils :refer (b)])
  (:require [clojure.string :as string]
            [schema.core :as sc]
            [tixi.schemas :as s]
            [tixi.geometry :as g :refer [Size Point]]
            [tixi.utils :refer [p]]
            [tixi.data :as d]))

;; Bresenham's line algorithm
;; http://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm
(defn- build-line-rest-coords [current x1 y1 x2 y2 err dx dy sx sy sym slash]
  (if (not (and (= x1 x2)
                (= y1 y2)))
    (let [e2 (bit-shift-left err 1)
          new-err (cond (and (> e2 (- dy)) (< e2 dx)) (+ (- err dy) dx)
                        (> e2 (- dy)) (- err dy)
                        (< e2 dx) (+ err dx)
                        :else err)
          new-x1 (cond (> e2 (- dy)) (+ x1 sx)
                       :else x1)
          new-y1 (cond (< e2 dx) (+ y1 sy)
                       :else y1)
          new-sym (if (or (and (= sym "|") (not= new-x1 x1))
                          (and (= sym "-") (not= new-y1 y1)))
                    slash
                    sym)]
      (build-line-rest-coords (assoc current (Point. x1 y1) new-sym) new-x1 new-y1 x2 y2 new-err dx dy sx sy sym slash))
    (assoc current (Point. x1 y1) sym)))

(defn- repeat-string [string times]
  (apply str (repeat times string)))


(defn build-line [data]
  (let [[x1 y1 x2 y2] data
        dx (.abs js/Math (- x2 x1))
        dy (.abs js/Math (- y2 y1))
        sx (if (< x1 x2) 1 -1)
        sy (if (< y1 y2) 1 -1)
        err (- dx dy)
        sym (if (< dx dy) "|" "-")
        slash (if (= sx sy) "\\" "/")]
    (build-line-rest-coords {} x1 y1 x2 y2 err dx dy sx sy sym slash)))

(defn concat-lines [line-coords]
  (let [all-coords (apply concat (map build-line line-coords))
        repeated-coords (keys (filter (fn ([[_ count]] (> count 1))) (frequencies (map first all-coords))))]
    (reduce
      (fn [new-coords coords] (assoc new-coords coords "+"))
      (into {} all-coords)
      repeated-coords)))

(defn sort-data [data]
  (apply array-map (flatten (sort-by (comp vec reverse g/values first) data))))

(defn generate-data [dimensions points]
  (let [{:keys [width height]} (g/incr dimensions)]
    (string/join "\n"
      (map string/join
        (partition
          width
          (str
            (reduce
              (fn [string [{:keys [x y]} sym]]
                (let [position (- (+ (* width y) x) (count string))]
                  (str string (repeat-string " " position) sym)))
              ""
              points)
            (let [[{:keys [x y]} sym] (last points)
                  last-position (+ (* width y) x)]
              (repeat-string " " (- (* width height) last-position)))))))))
