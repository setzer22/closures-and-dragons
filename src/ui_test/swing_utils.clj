(ns ui-test.swing-utils
  (:import [java.net URL]
           [javax.swing JFrame JPanel JLabel SwingUtilities]
           [javax.imageio ImageIO]
           [java.awt BorderLayout Graphics Color BasicStroke Polygon Point Image AlphaComposite]
           [java.awt.event MouseAdapter MouseEvent]))

(defn panel-tree [panel]
  (tree-seq #(pos? (.getComponentCount %))
            #(.getComponents %)
            panel))

(defn find-component [panel name]
  (let [t (panel-tree panel)]
    (first (drop-while #(not= name (.getName %)) t))))

