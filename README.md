# BUNDLE

BUNDLE is a model for finding isogloss bundles (typical feature distributions) in a dialect continuum.  Given a binary feature matrix (N features by L languages) it will construct clusters of features that have similar distributions in the L languages.  This is the opposite of many clustering models that cluster objects by their features.  Other notable properties of BUNDLE are:

* It will automatically infer the number of clusters in the data.
* It incorporates a simple _attestation model_ that assumes that a given fraction of the features in any language will go unattested.

The design and implementation of the probabilistic model underlying BUNDLE are discussed [here](www.github.com).

## Download BUNDLE

The Scala source code for CONTINUUM can be obtained by cloning this repository and running [sbt 0.13.1](www.scala-sbt.org/0.13.1/docs/Getting-Started/Setup.html).  However, most people would rather download the [latest build](www.github.com).  Unzip the build to find:

* `bundle-0.1.0.jar`, a fat jar file with two main classes:
  * `bundle.Bundle`: generates posterior distribution trace files.
  * `bundle.Align`: summarizes a random set partition variable.
* `pollex06/pollex06.conf`, a demo configuration file.
* `pollex06/pollex06-raw.tsv`, data for the demo.

## Run BUNDLE

```
java -cp bundle-0.1.0.jar bundle.Bundle pollex06/pollex06.conf
```

```
java -cp bundle-0.1.0.jar bundle.Align pollex06/pollex06.conf
```
