#!/usr/bin/env nbb
;; Parity between `realty.kumiai.resolution` (the .cljc the actor runs on)
;; and `kotoba/kumiai/resolution_core.kotoba` (the same judgement in Kotoba).
;;
;; Runs on nbb, so the check itself never starts a JVM.
;;
;; THREE OUTCOMES, kept apart on purpose:
;;   PASS     every case agreed
;;   FAIL     a case disagreed  -> exit 1
;;   SKIP     the compiled artifact is absent or would not instantiate
;;            -> exit 3, NOT 0. A skipped parity check must not read like a
;;            passing one; that is the failure this repository keeps naming.
;;
;; Availability is measured by IMPORTING the artifact and reading the error,
;; never by `which` or a bare file test -- the kotoba shim on this machine is
;; a two-line script that execs a deleted /tmp path, so it passes `which` and
;; dies with 126.
(ns kotoba.parity
  (:require [realty.kumiai.resolution :as r]
            ["node:path" :as path]
            ["node:url" :as url]))

(def artifact
  (or (first (filter some? [(aget js/process.env "KUMIAI_KOTOBA_MJS")]))
      "/tmp/claude-501/kout/resolution_core.mjs"))

;; Cases drawn from the six statutes the catalog carries, with every exact
;; boundary among them: 過半数 at exactly half, 3/4 at exactly three
;; quarters, 2/3 of 99 (which a float would round), NSW's quarter cap, and
;; SGP's 75% of the votes cast.
(def cases
  [;; [n base numer denom strict? label]
   [50 100 1 2 true  "JPN 第39条 exactly half of the attending"]
   [51 100 1 2 true  "JPN 第39条 one over"]
   [30 44  1 2 true  "JPN 第39条 the contested ballot"]
   [75 100 3 4 false "JPN 第17条 exactly three quarters"]
   [74 100 3 4 false "JPN 第17条 one under"]
   [66 99  2 3 false "JPN 第61条第5項 two thirds of 99, exact"]
   [65 99  2 3 false "JPN 第61条第5項 one under"]
   [56 70  4 5 false "JPN 第62条 four fifths"]
   [26 50  1 2 true  "DEU § 25 of the votes cast"]
   [45 60  2 3 true  "DEU § 21 Abs. 2 votes cast"]
   [5000 10000 1 2 true "DEU § 21 Abs. 2 exactly half of all shares"]
   [60 100 3 5 false "ESP 17.3 three fifths"]
   [34 100 1 3 false "ESP 17.1 one third"]
   [100 100 1 1 false "ESP 17.6 unanimity"]
   [99 100 1 1 false "ESP 17.6 one short of unanimity"]
   [6000 9000 2 3 false "FRA art. 26 exactly two thirds of the voices"]
   [50 100 1 2 true  "FRA art. 26 exactly half the members"]
   [3000 4000 3 4 false "SGP s 2(3) exactly 75% of the votes cast"]
   [2999 4000 3 4 false "SGP s 2(3) one under"]
   [1000 4000 1 4 false "AUS-NSW s 5(1) the quarter cap, at the cap"]
   [0 10 0 1 false "AUS-NSW s 5(3) no vote against"]])

(defn run [inst]
  (let [results
        (for [[n base numer denom strict? label] cases]
          (let [cmp (if strict? :greater-than :at-least)
                clj-met (r/meets? n base {:numer numer :denom denom} cmp)
                clj-bnd (r/on-boundary? n base {:numer numer :denom denom})
                clj-req (r/required base {:numer numer :denom denom} cmp true)
                ;; i64 crosses this boundary as BigInt -- the artifact
                ;; rejects a JS number with `invalid-i64` rather than
                ;; coercing it, which is the right refusal and the reason
                ;; this conversion is explicit.
                b (fn [x] (js/BigInt x))
                k-met ((aget inst "axis-met") (b n) (b base) (b numer) (b denom) strict?)
                k-bnd ((aget inst "axis-boundary") (b n) (b base) (b numer) (b denom))
                k-req ((aget inst "axis-required") (b base) (b numer) (b denom) strict?)]
            {:label label
             :met [clj-met k-met (= (boolean clj-met) (boolean k-met))]
             :bnd [clj-bnd k-bnd (= (boolean clj-bnd) (boolean k-bnd))]
             :req [clj-req k-req (= (double clj-req) (js/Number k-req))]}))
        bad (remove #(and (nth (:met %) 2) (nth (:bnd %) 2) (nth (:req %) 2)) results)]
    (println (str "PARITY\tCASES\t" (count results) "\tDISAGREEMENTS\t" (count bad)))
    (doseq [b bad]
      (println (str "  MISMATCH " (:label b)
                    " met=" (pr-str (take 2 (:met b)))
                    " boundary=" (pr-str (take 2 (:bnd b)))
                    " required=" (pr-str (take 2 (:req b))))))
    (when (zero? (count results))
      (println "REFUSING to report a pass: the case table is empty")
      (js/process.exit 2))
    (js/process.exit (if (seq bad) 1 0))))

;; Import failure is a SKIP; anything that goes wrong AFTER the module is in
;; hand is a FAIL. Collapsing the two would let a crash inside the comparison
;; be reported as "not built yet".
(-> (js/import (str "file://" artifact))
    (.then (fn [m]
             (try
               (run ((aget m "instantiateKotoba") #js {}))
               (catch :default e
                 (println (str "FAIL\tthe artifact loaded and then failed under test: "
                               (.-message e)))
                 (js/process.exit 1)))))
    (.catch (fn [e]
              (println (str "SKIP\tthe Kotoba artifact could not be loaded: " (.-message e)))
              (println (str "\tpath: " artifact
                            "  (build it: kotoba -M compile <abs>/kotoba/kumiai/resolution_core.kotoba"
                            " --target js-browser --output " artifact ")"))
              (js/process.exit 3))))
