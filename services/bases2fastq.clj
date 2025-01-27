{
 :name "bases2fastq",
 :path "bases2fastq",

 :argcard  {"--chemistry-version" 1
            "--demux-only" 0
            "--detect-adapters" 0
            "--error-on-missing" 0
            "--exclude-tile" 1
            "--filter-mask" 1
            "--flowcell-id" 1
            "--force-index-orientation" 0
            "--i1-cycles" 1, "--i2-cycles" 1
            "--include-tile" 1
            "--input-remote" 1
            "--kit-configuration" 1
            "--legacy-fastq" 0
            "--log-level" 1
            "--no-error-on-invalid" 0
            "--no-projects" 0
            "--num-threads" 1
            "--num-unassigned" 1
            "--output-remote" 1
            "--preparation-workflow" 1
            "--r1-cycles" 1, "--r2-cycles" 1
            "--run-manifest" 1
            "--settings" 1
            "--skip-qc-report" 1
            "--split-lanes" 0, "--strict" 0}

 ;; instructional data used in /help
 :description  "Convert Element Bio binary sequencer files to fastqs\n Switches:
--chemistry-version VERSION     Run parameters override, chemistry version.
--demux-only, -d                Generate demux files and indexing stats without generating FASTQ
--detect-adapters               Detect adapters sequences, overriding any sequences present in run manifest.
--error-on-missing              Terminate execution for a missing file (by default, missing files are skipped and execution continues). Also set by --strict.
--exclude-tile, -e SELECTION    Regex matching tile names to exclude. This flag can be specified multiple times. (e.g. L1.*C0[23]S.)
--filter-mask MASK              Run parameters override, custom pass filter mask.
--flowcell-id FLOWCELL_ID       Run parameters override, flowcell ID.
--force-index-orientation       Do not attempt to find orientation for I1/I2 reads (reverse complement). Use orientation given in run manifest.
--help, -h                      Display this usage statement
--i1-cycles NUM_CYCLES          Run parameters override, I1 cycles.
--i2-cycles NUM_CYCLES          Run parameters override, I2 cycles.
--include-tile, -i SELECTION    Regex matching tile names to include. This flag can be specified multiple times. (e.g. L1.*C0[23]S.)
--input-remote, NAME            Rclone remote name for remote ANALYSIS_DIRECTORY
--kit-configuration KIT_CONFIG  Run parameters override, kit configuration.
--legacy-fastq                  Legacy naming for FASTQ files (e.g. SampleName_S1_L001_R1_001.fastq.gz)
--log-level, -l LEVEL           Severity level for logging. i.e. DEBUG, INFO, WARNING, ERROR (default INFO)
--no-error-on-invalid           Skip invalid files and continue execution (by default, execution is terminated for an invalid file). Overridden by --strict options.
--no-projects                   Disable project directories (default false)
--num-threads, -p NUMBER        Number of threads (default 1)
--num-unassigned NUMBER         Max Number of unassigned sequences to report. Must be <= 1000 (default 30)
--output-remote, NAME           Rclone remote name for remote OUTPUT_DIRECTORY
--preparation-workflow WORKFLOW Run parameters override, preparation workflow.
--qc-only                       Quickly generate run stats for single tile without generating FASTQ. Use --include-tile/--exclude-tile to define custom tile set.
--r1-cycles NUM_CYCLES          Run parameters override, R1 cycles.
--r2-cycles NUM_CYCLES          Run parameters override, R2 cycles.
--run-manifest, -r PATH         Location of run manifest to use instead of default RunManifest.csv found in analysis directory
--settings SELECTION            Run manifest settings override. This option may be specified multiple times.
--skip-qc-report SELECTION      Do not generate HTML QC report.
--split-lanes                   Split FASTQ files by lane
--strict                        any invalid or missing input file will terminate execution"

 }

