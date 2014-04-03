# BUNDLE

BUNDLE is a model for finding isogloss bundles (typical feature distributions) in a dialect continuum.  Given a binary feature matrix (N features by L languages) it will construct clusters of features that have similar distributions in the L languages.  This is the opposite of many clustering models that cluster objects by their features.  Other notable properties of BUNDLE are:

* It will automatically infer the number of clusters in the data.
* It incorporates a simple _attestation model_ that assumes that a given fraction of the features in any language will go unattested.

The design and implementation of the probabilistic model underlying BUNDLE are discussed [here](http://www.github.com).

## Download BUNDLE

You can work with the Scala source code for BUNDLE by cloning this repository and running [sbt 0.13.1](http://www.scala-sbt.org/0.13.1/docs/Getting-Started/Setup.html).  However, most people would rather download the [latest build](http://www.github.com).  Unzip the build to find:

* `bundle-0.1.0.jar`, a fat jar file with two main classes:
  * `bundle.Bundle` generates posterior distribution trace files.
  * `bundle.Align` summarizes a random set partition variable.
* `pollex06/pollex06.conf`, a demo configuration file.
* `pollex06/pollex06-raw.tsv`, data for the demo.

## Run BUNDLE on demo data

Before running, take a look at the configuration file `pollex06/pollex06.conf`:

```
data        pollex06-raw.tsv  # Input filename for binary matrix data.
base        pollex06          # Output filenames all begin with this string.
chainlen    10000             # Run MCMC for 10000 states.
burnin      2000              # Discard first 2000 states.
infofreq    10                # Report to the terminal every 10 states.
dumpfreq    100               # Sample every 100 states.
```

The data file `pollex06-raw.tsv` codes whether each of 4220 etyma exist in each of 40 Polynesian or Melanesian languages.  Each etymon is thus considered a linguistic feature.  This data was obtained from a 2006 version of [POLLEX](http://pollex.org.nz), a very larger comparative Polynesian word list.  It takes the form of a 4220×40 binary matrix with tab-separated values.  The first row contains labels for languages; the first column contains labels for features.  The rest of the cells contain either `0` or `1`.

Now run:

```
java -cp bundle-0.1.0.jar bundle.Bundle pollex06/pollex06.conf
```

It will take several hours to run to completion.  Every 10 states it will write a short summary of the MCMC state to `pollex06.out` and also to the terminal.  For example, it will write:

```
--- 540 ---
[α   4.9801] [ρμ   0.4600 ρλ   0.9351] [ζλ   4.6242] [K  39]
789 421 389 351 328 301 290 232 173 168 150 108 104 101 48 45 42 32 27 25 17 16 12 7 7 6 6 6 5 3 2 2 1 1 1 1 1 1 1
[ll   -120792.13] [z    -11563.19] [y    -66648.44] [x    -42580.50]
MTA 14 SAA 23 NGG 19 LAU 13 NGU 19 WYA 18 FIJ 62 ROT 58 TON 98 NIU 90
EUV 93 EFU 95 SAM 96 TOK 72 ECE 83 WUV 80 WFU 73 MAE 84 MFA 71 TIK 96
ANU 70 REN 91 PIL 77 KAP 77 NKO 84 TAK 87 OJA 82 SIK 78 NGR 55 PUK 92
EAS 72 MAO 98 TUA 91 PEN 91 RAR 95 MIA 11 TAH 90 HAW 87 MQA 93 MVA 74
```

* First line: The MCMC is at state 540.
* Second line: `α`, `ρμ`, `ρλ`, `ζλ` are model hyperparameters.  `K` is the number of clusters, which varies from sample to sample.
* Third line: These numbers are the size of each cluster, starting from the largest.  They sum to 4220, the number of etyma in the model.
* Fourth line: `ll` is the log likelihood of the model given . . .
* Other lines: Inferred lexicographic coverage rate for each language, normalized to 99.For example, the model believes that the XXX etyma attested for Anuta (ANU) comprise just 70% of the actual etyma in Anuta that also occur in the dataset.

Every 100 states BUNDLE emits a trace of the entire model state over several files:

* `pollex06.log`
* `pollex06.z.log`
* `pollex06.y.100.hex`, etc.

When `Bundle` completes, run:

```
java -cp bundle-0.1.0.jar bundle.Align pollex06/pollex06.conf
```

