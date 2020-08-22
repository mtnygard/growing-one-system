#! /bin/bash
clojure -A:depstar -m hf.depstar.uberjar clojupyter-standalone.jar
clojure -m clojupyter.cmdline install --ident mykernel-1 --jarfile clojupyter-standalone.jar
