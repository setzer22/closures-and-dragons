(ns ui-test.swing-wrappers
  (:import [java.net URL]
           [javax.swing JFrame JPanel JLabel SwingUtilities BoxLayout JButton JTextField Box BorderFactory]
           [javax.imageio ImageIO]
           [java.awt BorderLayout Graphics Color BasicStroke Polygon Point Image AlphaComposite RenderingHints Component GridBagLayout Dimension]
           [java.awt.event MouseAdapter MouseEvent])
  (:require [clojure.reflect :as r]
            [ui-test.grid-protocol :as grid]
            [ui-test.world-commands :as wc]))

(defn box-layout [orientation & children]
  (let [panel (JPanel.)
        layout (BoxLayout. panel (if (= orientation :vertical)
                                   BoxLayout/PAGE_AXIS
                                   BoxLayout/LINE_AXIS))]
    (.setLayout panel layout)
    (doseq [child children]
      (.add panel child))
    panel))

(defn horizontal-layout [& args]
  (apply box-layout :horizontal args))

(defn vertical-layout [& args]
  (let [layout (apply box-layout :vertical args)]
    (doseq [component (.getComponents layout)]
      (.setAlignmentY component Component/TOP_ALIGNMENT))
    layout))


(defn get-gbc-method [instance method-name]
  (as-> instance $$
    (.getClass $$)
    (.getMethods $$)
    (drop-while #(not= (.getName %) method-name) $$)
    (first $$)))

(defn setter-of [field]
  (let [[x & xs] (seq field)
        cap-field (apply str (.toUpperCase (str x)) xs)]
    (str "set" cap-field)))


(defn gbc-constraints [[[position x y :as wat] & constraints]]
  (when (not= position :position) (throw (Exception. "The first action should always be :setPosition")))
  (let [gbc (main.java.GBC. x y)] 
    (doseq [[method-name & args] constraints
            :let [constraint-method (get-gbc-method gbc (setter-of (name method-name)))]]
      (.invoke constraint-method gbc 
               (into-array (map #(if (= (type %) java.lang.Long) (int %) %) 
                                args))))
    gbc))

(defn grid-bag-layout [& objects-with-constraints]
  (let [panel (JPanel. (GridBagLayout.))]
    (doseq [[object & constraints] objects-with-constraints]
      (.add panel object (gbc-constraints constraints)))
    panel))

(comment "grid-bag-layout usage examples:" 
         (grid-bag-layout 
           [(button "heh")
            [:position 0 0]
            [:span 2 1]
            [:fill main.java.GBC/BOTH]
            [:weight 0.0 1.0]])

         (grid-bag-layout 
           [(label "Hello World") 
            [:position 3 2]
            [:anchor 5]]))

(defn border-layout [& {:keys [north south east west center]}]
  (let [panel (JPanel.)]
    (.setLayout panel (BorderLayout.))
    (when north (.add panel north BorderLayout/PAGE_START))
    (when south (.add panel south BorderLayout/PAGE_END))
    (when east (.add panel east BorderLayout/LINE_START))
    (when west (.add panel west BorderLayout/LINE_END))
    (when center (.add panel center BorderLayout/CENTER))
    panel))

(defn space []
  (Box/createHorizontalGlue))

(defn flow-layout [& children]
  (let [panel (JPanel.)]
    (doseq [child children]
      (.add panel child))
    panel))

(defn label 
  ([text] (doto (JLabel. text)
            (.setVisible true)))
  ([name text] (doto (label text)
                 (.setName name))))

(defn frame [title] (JFrame. title))

(defn text-field [text] (JTextField. text))

(defn button [name] (JButton. name))

(defn labeled-input 
  [label-text]
  (flow-layout 
    (label label-text)
    (text-field "Enter data")))


(defmacro with-graphics [g & body]
  `(let [~g (.create ~g)]
     ~@body
     (.dispose ~g)))

(defn grid-panel [name world]
  (doto 
    (proxy [JPanel] [] 
      (paintComponent [^Graphics g] 
        (proxy-super paintComponent g)
        (let [{:keys [dimensions tile-size tokens]} @world
              R (grid/token-radius @world)] 
          (with-graphics g 
            (.setColor g (Color/black))
            (.setStroke g (BasicStroke. 2))
            (.setRenderingHint g RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
            (grid/draw-grid! @world g [0 20] [0 20])
            (doseq [{:keys [x y img] :as token} (wc/get-all-tokens @world)]
              (.drawImage g img (- x R) (- y R) (* 2 R) (* 2 R) nil))))))
    (.setName name)
    (.setBackground Color/white)
    (.setMinimumSize (Dimension. 800 600))
    (.setPreferredSize (Dimension. 800 600))))


(defn make-window! [gui title]
  (SwingUtilities/invokeLater 
    (fn [] 
      (let [frame (frame title)]
        (.add frame gui)
        (.setVisible gui true) []
        (.setVisible frame true)))))

