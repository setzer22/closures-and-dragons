(ns ui-test.hex-drawing
  (:import [javax.swing JFrame JPanel JLabel SwingUtilities]
           [java.awt BorderLayout Graphics Graphics2D Color BasicStroke Polygon Point GraphicsEnvironment GraphicsDevice GraphicsConfiguration Transparency RenderingHints]
           [java.awt.geom Path2D Path2D$Double Path2D$Float]
           [java.awt.image BufferedImage]
           [java.awt.event MouseAdapter MouseEvent])
  (:require [ui-test.grid-protocol :as grid-protocol]
    [clojure.core.matrix :as m])
  (:gen-class))

(m/set-current-implementation :vectorz)

(defmacro defmemo [name args & body]
  `(def ~name (memoize (fn ~args ~@body))))

(def sin30 (Math/sin (/ Math/PI 6)))
(def cos30 (Math/cos (/ Math/PI 6)))

(defn hex-A [size] (* size sin30))
(defn hex-B [size] (* size cos30))
(defmemo hex-center-offset [size] [(hex-B size) (+ (/ size 2) (hex-A size))])

(defmacro with-hex-dim [size & body]
  `(let [~'C ~size
         ~'A (hex-A ~size)
         ~'B (hex-B ~size)]
     ~@body))

(defn H->S [size] 
  (with-hex-dim size 
    (m/matrix [[(* 2 B) B]
               [0 (+ C A)]])))

(defn S->H [size]
  (m/inverse (H->S size)))

(defn v+ [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn v- [[x1 y1] [x2 y2]]
  [(- x1 x2) (- y1 y2)])

(defn axial->cubic [[q r]]
  [q (+ (- q) (- r)) r])

(defn cubic->axial [[x y z]]
  [x z])

(defn cube-round [hex]
  (let [[x y z] (axial->cubic hex)
        rx (Math/round x)
        ry (Math/round y)
        rz (Math/round z)

        dx (Math/abs (- rx x))
        dy (Math/abs (- ry y))
        dz (Math/abs (- rz z))]
    (cubic->axial 
      (cond (and (> dx dy) (> dx dz)) [(+ (- ry) (- rz)), ry, rz]
            (> dy dz)                 [rx, (+ (- rx) (- rz)), rz]
            :else                     [rx, ry, (+ (- rx) (- ry))]))))

(defn pixel->hex [size pixel]
  (cube-round (into [] (m/mmul (S->H size) pixel))))

(defn hex->pixel [size hex]
  (into [] (m/mmul (H->S size) hex)))


(defn horiz-hex-coords [size center]
  (with-hex-dim size
    (mapv #(v+ center %) 
          [[0 (+ A C)]
           [0 A]
           [B 0]
           [(* 2 B) A]
           [(* 2 B) (+ A C)]
           [B (* 2 C)]])))

(defn hex-grid-positions [size [w-min w-max] [h-min h-max]]
  (with-hex-dim size 
    (for [i (range h-min h-max)
          j (range w-min w-max)]
      (v- [(if (zero? (mod i 2))
             (* j 2 B)
             (+ (* j 2 B) B))
           (* i (+ A C))]
          (hex-center-offset size)))))

(defn horiz-hex [radius center]
   (let [coords (horiz-hex-coords radius center)
         x-values (int-array (map first coords))
         y-values (int-array (map second coords))]
     (Polygon. x-values y-values 6)))

(defn default-graphics-config [] 
  (.getDefaultConfiguration 
    (.getDefaultScreenDevice 
      (GraphicsEnvironment/getLocalGraphicsEnvironment))))

(defmemo hex-image [size]
  (with-hex-dim size 
    (let [^GraphicsConfiguration config (default-graphics-config)
          ^BufferedImage img (.createCompatibleImage config (* 2 B) (+ C (* 2 A)) Transparency/TRANSLUCENT)
          ^Graphics2D g2 (.createGraphics img)]
      (.setColor g2 (Color/black))
      (.setRenderingHint g2 RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
      (.draw g2 (horiz-hex size [0 0]))
      img)))

(defn draw-hex-grid! [size ^Graphics2D g q-bounds r-bounds] 
  (time 
    (with-hex-dim size 
      (let [hex-img (hex-image size)] 
      (doseq [[x y] (hex-grid-positions size q-bounds r-bounds)]
        (.drawImage g hex-img x y (* 2 B) (+ C (* 2 A)) nil))))))

(defmethod grid-protocol/pixel->tile :pointy-hex
  [{:keys [size]} pixel]
  (pixel->hex size pixel))

(defmethod grid-protocol/tile->pixel :pointy-hex
  [{:keys [size]} tile]
  (hex->pixel size tile))

(defmethod grid-protocol/snap-to-grid :pointy-hex
  [{:keys [size]} pixel]
  (hex->pixel size (pixel->hex size pixel)))

(defmethod grid-protocol/token-radius :pointy-hex
  [{:keys [size]}]
  (hex-B size))

(defmethod grid-protocol/draw-grid! :pointy-hex
  [{:keys [size]} ^Graphics g q-bounds r-bounds]
  (draw-hex-grid! size g q-bounds r-bounds))

