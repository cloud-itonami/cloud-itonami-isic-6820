#!/usr/bin/env nbb
;; JVM-free acceptance for the Kotoba judgement core.
;;
;; Q9 (CLAUDE.md) asks that build AND acceptance stay off the JDK, and that
;; `java` / `javac` / `clojure` / `clj` be DENIED AND TRACED rather than
;; merely absent -- so that a path which quietly reaches for one fails here
;; instead of succeeding somewhere else.
;;
;; This runs on nbb. It builds a directory of stub `java`/`javac`/`clojure`/
;; `clj`/`jarsigner` that append to a trace file and exit 97, puts it FIRST on
;; PATH, poisons JAVA_HOME, and then:
;;
;;   amu check   --jvm-free
;;   amu compile --jvm-free --target <native>        -> .kexe
;;   amu compile --jvm-free --target wasm32-browser  -> .wasm
;;   amu extract-native --symbol main                -> raw code + offset
;;   cc tools/kexe_loader.c                          -> loader
;;   loader main.bin <offset> 0 <isa> -                -> the guest's own count
;;
;; The guest's `main` returns the NUMBER OF FAILED self-checks, so 0 is the
;; only passing answer and a single regression is distinguishable from a
;; broken build.
;;
;; Then it asserts the trace file is EMPTY. `amu test` is deliberately not in
;; the list: measured 2026-09-06 it routes to `clojure -M:run`, and it is the
;; only amu subcommand that trips the denial.
;;
;; It then runs ORACLE PARITY, also without a JVM: the `.cljc` the actor runs
;; on is loaded by nbb, the Kotoba side is called through the NATIVE artifact
;; via the loader (bools cross that ABI as 0/1), and the two are compared over
;; every exact boundary in the six statutes. Q9 allows the oracle to be
;; nbb/CLJS, and this keeps the artifact under test the native one rather than
;; a js build, which would drag `clojure` back in.
;;
;; Exit: 0 pass · 1 fail · 2 COULD NOT MEASURE (toolchain absent).
;; 2 is neither of the other two on purpose -- a run that could not build is
;; not a run that found nothing wrong.
(ns kotoba.native-acceptance
  (:require [realty.kumiai.resolution :as oracle]
            ["node:child_process" :as cp]
            ["node:fs" :as fs]
            ["node:os" :as os]
            ["node:path" :as path]))

(def root (or (aget js/process.env "KUMIAI_REPO") (.cwd js/process)))
(def amu (or (aget js/process.env "AMU_BIN")
             (.resolve path root ".." ".." "kotoba-lang" "amu" "bin" "amu")))
(def amu-root (.dirname path (.dirname path amu)))
(def source (.resolve path root "kotoba" "kumiai" "resolution_core.kotoba"))

(def isa (if (= "arm64" (.-arch js/process)) "aarch64" "x86_64"))
(def native-target (str isa "-" (if (= "darwin" (.-platform js/process)) "macos" "linux")))

(def work (.mkdtempSync fs (.join path (.tmpdir os) "kumiai-acceptance-")))
(def trace (.join path work "jvm-trace.log"))
(def deny (.join path work "deny"))

(defn make-deny! []
  (.mkdirSync fs deny #js {:recursive true})
  (doseq [c ["java" "javac" "clojure" "clj" "jarsigner"]]
    (let [p (.join path deny c)]
      (.writeFileSync fs p (str "#!/bin/sh\necho \"JVM-DENIED: " c " $*\" >> " trace "\nexit 97\n"))
      (.chmodSync fs p 0755)))
  (.writeFileSync fs trace ""))

(defn sh [cmd args]
  (let [env (js/Object.assign #js {} js/process.env
                              #js {"PATH" (str deny ":" (aget js/process.env "PATH"))
                                   "JAVA_HOME" "/nonexistent"})
        r (.spawnSync cp cmd (clj->js args) #js {:encoding "utf8" :env env :timeout 900000})]
    {:status (.-status r) :out (str (.-stdout r)) :err (str (.-error r))}))

(defn traced [] (let [t (.readFileSync fs trace "utf8")]
                  (remove empty? (.split t "\n"))))

(defn fail [msg] (println (str "FAIL\t" msg)) (js/process.exit 1))
(defn cannot [msg] (println (str "COULD-NOT-MEASURE\t" msg)) (js/process.exit 2))

(defn -main []
  (when-not (.existsSync fs amu) (cannot (str "amu driver not at " amu)))
  (when-not (.existsSync fs source) (cannot (str "source not at " source)))
  (make-deny!)
  (let [kexe (.join path work "rc.kexe")
        wasm (.join path work "rc.wasm")
        binf (.join path work "main.bin")
        loader (.join path work "kexe-loader")
        chk (sh amu ["check" source "--jvm-free"])]
    (when-not (re-find #":ok true" (:out chk)) (fail (str "amu check: " (:out chk))))

    (let [n (sh amu ["compile" source "--jvm-free" "--target" native-target "--output" kexe])]
      (when-not (re-find #":ok true" (:out n)) (fail (str "native compile: " (:out n)))))
    (let [w (sh amu ["compile" source "--jvm-free" "--target" "wasm32-browser" "--output" wasm])]
      (when-not (re-find #":ok true" (:out w)) (fail (str "wasm compile: " (:out w)))))

    (let [x (sh amu ["extract-native" kexe "--symbol" "main" "--output" binf])
          off (second (re-find #":offset (\d+)" (:out x)))]
      (when-not off (fail (str "extract-native: " (:out x))))
      (let [c (sh "cc" ["-O2" "-o" loader (.join path amu-root "tools" "kexe_loader.c")])]
        (when-not (zero? (:status c)) (cannot (str "cc could not build the kexe loader: " (:out c)))))
      (let [r (sh loader [binf off "0" isa "-"])
            failures (js/parseInt (.trim (:out r)) 10)]
        (when (js/isNaN failures) (fail (str "loader produced no count: " (pr-str (:out r)))))
        (println (str "NATIVE\t" native-target "\tself-check-failures\t" failures))
        ;; ---- oracle parity, against the NATIVE artifact ----
        (let [sym (fn [name out]
                    (let [x (sh amu ["extract-native" kexe "--symbol" name "--output" out])]
                      (when-not (re-find #":ok true" (:out x))
                        (fail (str "extract-native " name ": " (:out x))))
                      {:bin out
                       :off (second (re-find #":offset (\d+)" (:out x)))
                       :arity (second (re-find #":arity (\d+)" (:out x)))}))
              call (fn [{:keys [bin off arity]} args]
                     (let [r (sh loader (concat [bin off arity isa "-"] (map str args)))]
                       (js/parseInt (.trim (:out r)) 10)))
              met (sym "axis-met" (.join path work "am.bin"))
              bnd (sym "axis-boundary" (.join path work "ab.bin"))
              req (sym "axis-required" (.join path work "ar.bin"))
              ;; every exact boundary the six statutes turn on, plus the
              ;; two-thirds-of-99 case a float would round
              cases [[50 100 1 2 true] [51 100 1 2 true] [30 44 1 2 true]
                     [75 100 3 4 false] [74 100 3 4 false]
                     [66 99 2 3 false] [65 99 2 3 false]
                     [56 70 4 5 false] [26 50 1 2 true] [45 60 2 3 true]
                     [5000 10000 1 2 true] [60 100 3 5 false] [34 100 1 3 false]
                     [100 100 1 1 false] [99 100 1 1 false]
                     [6000 9000 2 3 false] [3000 4000 3 4 false] [2999 4000 3 4 false]
                     [1000 4000 1 4 false] [0 10 0 1 false]]
              bad (for [[n base numer denom strict?] cases
                        :let [cmp (if strict? :greater-than :at-least)
                              o-met (boolean (oracle/meets? n base {:numer numer :denom denom} cmp))
                              o-bnd (boolean (oracle/on-boundary? n base {:numer numer :denom denom}))
                              o-req (double (oracle/required base {:numer numer :denom denom} cmp true))
                              k-met (= 1 (call met [n base numer denom (if strict? 1 0)]))
                              k-bnd (= 1 (call bnd [n base numer denom]))
                              k-req (double (call req [base numer denom (if strict? 1 0)]))]
                        :when (not (and (= o-met k-met) (= o-bnd k-bnd) (= o-req k-req)))]
                    (str n "/" base " " numer "/" denom (if strict? " strict" " at-least")
                         " met " o-met "|" k-met " boundary " o-bnd "|" k-bnd
                         " required " o-req "|" k-req))]
          (println (str "PARITY\tCASES\t" (count cases) "\tDISAGREEMENTS\t" (count bad)))
          (doseq [b bad] (println (str "  MISMATCH " b)))
          (let [t (traced)]
            (println (str "JVM-INVOCATIONS\t" (count t)))
            (doseq [line t] (println (str "  " line)))
            (cond
              (seq t) (fail "the JVM-free path invoked a JDK binary")
              (pos? failures) (fail (str failures " self-checks failed inside the guest"))
              (seq bad) (fail (str (count bad) " cases disagree with the .cljc oracle"))
              :else (do (println "PASS\tkotoba compile + amu native, oracle parity clean, no JVM")
                        (js/process.exit 0)))))))))

(-main)
