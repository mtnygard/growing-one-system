(ns igles.views
  (:require-macros [igles.views :refer [handle]])
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [igles.routes :as routes]
            [igles.util :refer [<sub >evt >dis]]))

(defn +class
  "Add a CSS class to the given element."
  [v cl]
  (let [[tag & more] v
        opts         (if (map? (first more)) (first more) {})
        more         (if (map? (first more)) (rest more) more)]
    (apply vector tag (update opts :class conj cl) more)))

(defn +classes
  "Add multiple CSS classes to the given element"
  [v cls]
  (reduce +class v cls))

(defn variant
  "Return a function that is like f but also adds the given class. Useful for families of items (like buttons) in Bootstrap."
  [f cl]
  (let [modifier (if (coll? cl) +classes +class)]
    (fn [& c]
      (modifier (f c) cl))))

;;
;; Navbar component
;;
(defn navbar-dropdown-menu [title & items]
  (let [id (str/lower-case title)]
    [:li.nav-item.dropdown
     [:a.nav-link.dropdown-toggle {:id            id
                                   :href          "#"
                                   :data-toggle   "dropdown"
                                   :aria-haspopup "true"
                                   :aria-expanded "false"} title]
     (let [->hic        (fn [x] (let [href  (if (vector? x) (second x) (str "#" (str/lower-case x)))
                                      title (if (vector? x) (first x) x)]
                                  [:a.dropdown-item.page-scroll {:href href} title]))
           hiccup-items (map ->hic items)]
       (apply vector :div.dropdown-menu {:aria-labelledby id} hiccup-items))]))

(defn navbar-inpage-item [& xs]
  (let [opts  (if (map? (first xs)) (first xs) {})
        title (if (map? (first xs)) (second xs) (first xs))
        opts  (merge {:href (str "#" (str/lower-case title))} opts)]
    [:li.nav-item [:a.nav-link.page-scroll opts title]]))

(defn navbar-appsection-item [route title]
  [:li.nav-item [:a.nav-link {:on-click #(>dis :navigate route)} title]])

(defn navbar-clickable-logo [src]
  [:a.navbar-brand {:on-click #(>dis :navigate "/")}
   [:img.nav-brand-logo {:src src}]])

(defn navbar-compressed-icon [collapse-target icon-class]
  [:button.navbar-toggler.navbar-toggler-right {:data-toggle   "collapse"
                                                :data-target   (str "#" collapse-target)
                                                :aria-controls collapse-target
                                                :aria-expanded "false"
                                                :aria-label    "Toggle navigation"}
   [:span {:class icon-class}]])

(defn- navclasses [scroll-top]
  (if (>= 200 scroll-top)
    ["navbar" "navbar-toggleable-md" "fixed-top" "sticky-navigation-alt" "navbar-transparent"]
    ["navbar" "navbar-toggleable-md" "fixed-top" "sticky-navigation-alt" "bg-inverse" "navbar-raised"]))

(defn navbar-contents [collapse-target contents]
  [:div.collapse.navbar-collapse
   {:id collapse-target}
   (apply vector :ul.navbar-nav.ml-auto contents)])

(defn navbar [& items]
  [:nav
   [navbar-compressed-icon "navbarCollapse" "ion-grid"]
   [navbar-clickable-logo "/img/logo-w60.png"]
   [navbar-contents "navbarCollapse" items]])

;;
;; Layout
;;
(defn content-section [classes & blocks]
  [:section.colored-section {:class classes}
   (apply vector :div.container blocks)])

(defn row [& contents]
  (apply vector :div.row contents))

(defn row-full-width [& contents]
  [:div.row
   (apply vector :div.col-md-8.offset-md-2 contents)])

(defn col [contents]
  (apply vector :div.col contents))

(def col-sm-3 (variant col ["offset-md-2" "col-sm-3"]))

;;
;; Typography
;;
(defn title [& c]
  (let [opts (if (map? (first c)) (first c) {})
        c    (if (map? (first c)) (rest c) c)]
    (apply vector :div.title opts c)))

(defn h1 [s]
  [:h1 s])

(defn h2 [s]
  [:h2.heading-semi-black s])

(defn h3 [s]
  [:h3 s])

(defn h4 [s] [:h4 s])
(defn h5 [s] [:h5.text-uppercase.mb-5 s])

(defn muted [s]
  [:p.lead.text-muted s])

;; Inputs
(defn text-input [model placeholder]
  [:input {:class "form-control"
           :type "text"
           :placeholder placeholder
           :value @model
           :on-change (handle
                       (let [new-val (-> event .-target .-value)]
                         (reset! model new-val)))}])

;; Icons

(defn icon [nm] [:> "ion-icon" {:name nm
                                :size "small"}])
(def icon-create (partial icon "add"))

;;
;; Buttons
;;
(defn button [c]
  (apply vector :button.btn c))

(def button-primary   (variant button "btn-primary"))
(def button-secondary (variant button "btn-secondary"))
(def button-outline   (variant button "btn-outline-primary"))
(def button-link      (variant button "btn-link"))
(def button-round     (variant button ["btn-primary" "btn-round"]))
(def button-circle    (variant button ["btn-primary" "btn-circle"]))

;;
;; Build the actual UI
;;

;; Placeholder for when we can't do anything, just put empty content.
(defn unavailable []
  [content-section ["bg-inverse"]
   [title [h2 "This part isn't written yet"]]])

;; Manage worlds
(defn worlds []
  [:<>
   [content-section ["bg-inverse"]
    [title [h2 "Worlds"]]]
   (+class [row-full-width
            [button-primary {:on-click #(>dis :navigate (routes/new))} [icon-create] "New World"]] "mt-2")])

;; Accept observations


(defn capture-panel
  []
  [:div
   [h1 "Count Clicker"]
   [muted (str "Clicked " @(rf/subscribe [:counter]) " times")]
   [button-primary {:on-click #(rf/dispatch [:counter-clicked])} "Click This!"]
   [button-secondary {:disabled (not (<sub :can-submit?))
                      :on-click #(rf/dispatch [:submit-score])} "Submit your score"]])

;; Show off some components (also a testing ground for styles)
(defn demo
  []
  [content-section ["bg-inverse"]
   [title [h2 "Basic Components"]]
   [:div {:id "buttons"}
    [title [h3 "Buttons"]
     [muted "Multiple Styles"]]
    [row-full-width
     [button-primary "Default 2"]
     [button-outline "Outline"]
     [button-round "Round"]
     [button-round [:em.ion-android-checkmark-circle] "with icon"]
     [button-circle [:em.ion-power]]
     [button-link "Link"]]
    [+class [title [muted "Multiple Sizes"]] "mt5"]

    [row-full-width
     [+class [button-primary "Extra Small"] "btn-xs"]
     [+class [button-primary "Small"] "btn-sm"]
     [button-primary "Regular"]
     [+class [button-primary "Medium"] "btn-md"]
     [+class [button-primary "Large"] "btn-lg"]]]])

;; Create a new World

(defn create-world-panel
  []
  (let [world-name (r/atom nil)]
    (fn []
      [content-section ["bg-inverse"]
       [row-full-width
        [title [h2 "Create a new world"]
         [muted "A world contains a schema and observations within that schema."]]]
       [row
        [col-sm-3
         [:div.form-group
          [text-input world-name "World name"]]
         [button-primary {:on-click (>evt :create-world @world-name)} "Create World"]]]])))

;; Welcome users
(defn front-page
  []
  [content-section ["hero-header"]
   [row-full-width
    [:div.brand
     (+class (h1 "Know More Tomorrow") "text-white")
     (h5 "Observe and record facts about the world")]]])

;; Map routes to views
(def ^:private view->panel
  {:home    [front-page]
   :demo    [demo]
   :capture [capture-panel]
   :user  [worlds]
   :create-world [create-world-panel]})

(defn view-for [route]
  (view->panel (:view (:data route)) [front-page]))

;; Bring it all together
(defn main-view
  []
  [:<>
   (+classes
    (navbar
     [navbar-appsection-item (routes/demo) "Demo"]
     [navbar-appsection-item (routes/worlds) "Worlds"])
    (navclasses (<sub :scroll-top)))

   [:div.wrapper
    [view-for (<sub :active-route)]]])
