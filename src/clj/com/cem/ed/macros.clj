(ns com.cem.ed.macros)

(defmacro ->hash [& vars]
  (list `zipmap
        (mapv keyword vars)
        (vec vars)))


(defmacro args
  "Converts (args a b c) -> (assoc args :a a :b b :c c)"
  [& vars]
  (let [xs (interleave (mapv keyword vars)
                       (vec vars))]
    `(assoc ~'args ~@xs)))


(defmacro binding-block [let-mode & body]
  (cond
    ;; no form is nil
    (= (count body) 0) nil

    ;; one thing remaining is the return value
    (= (count body) 1)
    (first body)

    (list? (first body))
    (let [[x & xs] body]
      `(do ~x
           (binding-block ~let-mode ~@xs)))

    ;; keyword special detected
    (keyword? (first body))
    (let [[_ x & xs] body
          [k a b & cs] body]
      (case k
        (:let :plet :pplet) `(binding-block ~k ~@(rest body))
        :do `(do ~x
                 (binding-block ~let-mode ~@xs))
        :pdo `(promesa.core/do
                ~x
                (binding-block ~let-mode ~@xs))
        (:when :return-when) `(if ~a
                                ~b
                                (binding-block ~let-mode ~@cs))
        :with-open `(clojure.core/with-open [~a ~b]
                      (binding-block ~let-mode ~@cs))))

    ;; collect let forms and recurse
    :else
    (let [[let-forms remaining-body]
          (loop [let-forms []
                 remaining-body body]
            (if (<= (count remaining-body) 1)
              [let-forms remaining-body]
              (let [[a b & xs] remaining-body]
                (if-not (or (keyword? a) (list? a))
                  (recur (conj let-forms a b)
                         xs)
                  [let-forms remaining-body]))))

          -let (condp = let-mode
                 :let 'clojure.core/let
                 :plet 'promesa.core/let
                 :pplet 'promesa.core/plet)]
      (if (< 0 (count let-forms))
        `(~-let [~@let-forms]
                (binding-block ~let-mode ~@remaining-body))
        `(binding-block ~let-mode ~@remaining-body)))))

(defmacro bb
  "A shorthand for `binding-block` with `:let` mode.
  :when
  :return-when"
  [& body]
  (let [[let-mode body] (case (first body)
                          (:let :plet :pplet) [(first body) (rest body)]
                          [:let body])]
    `(binding-block ~let-mode ~@body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro enable-obj-bitfield-option! [o field flag]
  `(let [x# (. ~o ~field)]
     (set! (. ~o ~field) (bit-or x# ~flag))))

(defmacro disable-obj-bitfield-option! [o field flag]
  `(let [x# (. ~o ~field)]
     (set! (. ~o ~field) (bit-and x# (bit-not ~flag)))))
