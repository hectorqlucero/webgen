(defproject webgen/lein-template "0.1.1"
  :description "WebGen/LST Parameter-Driven Web Application Template"
  :url "https://github.com/hectorqlucero/webgen"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :deploy-repositories [["clojars" {:url "https://repo.clojars.org"
                                    :username :env/CLOJARS_USERNAME
                                    :password :env/CLOJARS_PASSWORD
                                    :sign-releases false}]]
  :eval-in-leiningen true)
