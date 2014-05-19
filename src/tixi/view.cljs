(ns tixi.view
  (:require-macros [dommy.macros :refer (node sel1)]
                   [schema.macros :as scm]
                   [cljs.core.async.macros :refer [go]])
  (:require [quiescent :as q :include-macros true]
            [quiescent.dom :as d]
            [dommy.core :as dommy]
            [cljs.core.async :as async :refer [>!]]
            [tixi.schemas :as s]
            [tixi.utils :refer [p]]
            [tixi.position :as p]
            [tixi.drawer :as drawer]))

(enable-console-print!)

(defn- send-event-with-coords [action type event channel]
  (let [nativeEvent (.-nativeEvent event)
        text-coords (p/text-coords-from-event nativeEvent)]
    (go (>! channel {:action action :type type :value text-coords :event nativeEvent}))))

(defn- selection-position [edges]
  (let [[x1 y1 x2 y2] edges
        [small-x large-x] (sort [x1 x2])
        [small-y large-y] (sort [y1 y2])
        {left :x top :y} (p/position-from-text-coords small-x small-y)
        {width :x height :y} (p/position-from-text-coords (inc (.abs js/Math (- large-x small-x)))
                                                          (inc (.abs js/Math (- large-y small-y))))]
    {:left (str left "px") :top (str top "px") :width (str width "px") :height (str height "px")}))

(q/defcomponent Layer
  "Displays the layer"
  [{:keys [id item is-hover is-selected]}]
  (let [{:keys [origin dimensions text]} (:cache item)
        {:keys [x y]} (apply p/position-from-text-coords origin)
        {width :x height :y} (apply p/position-from-text-coords dimensions)]
    (d/pre {:className (str "canvas--content--layer"
                            (if is-selected " is-selected" "")
                            (if is-hover " is-hover" ""))
            :style {:left x :top y :width width :height height}
            :id (str "layer-" id)}
      text)))

(q/defcomponent Canvas
  "Displays the canvas"
  [data channel]
  (d/div {:className "canvas"
          :onMouseDown (fn [e] (send-event-with-coords :draw :down e channel))
          :onMouseUp (fn [e] (send-event-with-coords :draw :up e channel))}
    (apply d/div {:className "canvas--content"}
           (map
             (fn [[id item]] (Layer {:id id
                                     :item item
                                     :is-hover (= id (:hover-id data))
                                     :is-selected (some #{id} (get-in data [:selection :ids]))}))
             (if-let [{:keys [id item]} (:current data)]
               (assoc (:completed data) id item)
               (:completed data))))))

(q/defcomponent Selection
  "Displays the selection box around the selected item"
  [data channel]
  (let [selected-ids (get-in data [:selection :ids])
        edges (p/wrapping-edges selected-ids)
        [x1 y1 x2 y2] edges]
    (apply d/div {:className (str "selection" (when (> x1 x2) " is-flipped-x")
                               (when (> y1 y2) " is-flipped-y"))
                  :style (selection-position edges)}
      (map (fn [css-class]
             (d/div {:className (str "selection--dot selection--dot__" css-class)
                     :onMouseDown (fn [e] (send-event-with-coords (keyword (str "resize-" css-class)) :down e channel))
                     :onMouseUp (fn [e] (send-event-with-coords (keyword (str "resize-" css-class)) :up e channel))}))
           ["nw" "n" "ne" "w" "e" "sw" "s" "se"]))))

(q/defcomponent CurrentSelection
  "Displays the selection box around the selected item"
  [data channel]
  (let [edges (p/wrapping-coords (get-in data [:selection :current]))]
    (d/div {:className "current-selection" :style (selection-position edges)})))

(q/defcomponent Tool
  "Displays the currently selected tool"
  [data]
  (d/div {:className "tool"} (str (:tool data))))

(q/defcomponent Project
  "Displays the project"
  [data channel]
  (let [selected-ids (get-in data [:selection :ids])
        current-selection (get-in data [:selection :current])]
    (d/div {:className (str "project"
                            (cond
                              (some #{(:hover-id data)} selected-ids) " is-able-to-move"
                              (:hover-id data) " is-hover"
                              :else ""))}
      (Canvas data channel)
      (when (not-empty selected-ids)
        (Selection data channel))
      (when current-selection
        (CurrentSelection data channel))
      (when ())
      (Tool data))))

(scm/defn ^:always-validate render [data :- s/Data channel]
  "Renders the project"
  (q/render (Project data channel) (sel1 :#content)))
