#!/usr/bin/env ruby

require 'rubygems'
require 'rest_client'
require 'json'
require 'fileutils'
require 'tmpdir'
require 'socket'

#ARGV.each do|x|
#  puts "Argument: #{x}"
#end

#@server_names = ["roz", "babs", "liz", "prince"]
#@server_domains = ["bc.edu", "bc.edu", "bc.edu", "bc.edu"]
#@server_ports = ["7070", "7070", "7070", "7070"]
@server_names = ["prince", "liz"]
@server_domains = ["bc.edu", "bc.edu"]
@server_ports = ["7070", "7070"]

@servers = [@server_names,
            @server_domains,
            @server_ports].transpose.map {|n, d, p| "http://#{n}.#{d}:#{p}"}
@servers = Hash[*[@server_names, @servers].transpose.flatten]


@aerobioDir = File.expand_path("~/.aerobio")
@doneDir = File.join(@aerobioDir, "Finished")

if (!File.directory?(@aerobioDir))
  Dir.mkdir(@aerobioDir, 0775)
end

if (!File.directory?(@doneDir))
  Dir.mkdir(@doneDir, 0775)
end


@default_server_file =  File.join(@aerobioDir, "server")

def get_default_server ()
  default_file = @default_server_file
  if (File.exist?(default_file) and !(File.zero?(default_file)))
    return @servers[IO.readlines(default_file).first.chomp]
  else
    return @servers[Socket.gethostname]
  end
end

@default_server = get_default_server()




if (ARGV.length() == 0)
  puts "Use -h or --help for usage"
  exit(1)
end

sub_cmds = ["name-taxonomy"]


## Check for and pick off server if given
##
if (@server_names.include?(ARGV[0]))
  @server = @servers[ARGV[0]]
  @args = ARGV[1,ARGV.length]
else
  @server = nil
  @args = ARGV
end


# Pick off cmd and set cmd args
@cmd= @args[0]
args = @args[1,@args.length]


if (@cmd == "-h" or @cmd == "--help")
  puts "\nUsage:\n"
  puts "aerobio subcmd args"
  puts ""
  puts "args is the set of arguments appropriate to subcmd (including -h)"
  puts ""
  puts "subcmd is one of\n\n"
  puts " * server {<server-name> | 'any' | 'show' | 'activity' | 'available'}"
  puts " * list {['active' | 'done']}"
  puts " (The following subcmds may be prefaced by a server name)\n"
  puts " * run {<tool> | <func> | <job-flow>} {<args> | <args expid> | <expid>}"
  puts " * check-job {all | jobid+}"
  puts " * name-taxonomy"
  puts ""
  puts "Reports the results of subcmd's execution.  Typically 'success'"
  puts "or if failure, the exception text of failure"
  exit(0)
end

if (sub_cmds.include?(@cmd))
  argstr = args.join(" ")
  puts `#{@cmd} #{argstr}` # execute cmd, results go to term
  exit(0)
elsif (not ['server',
            'list',
            'run',
            'compare',
            'check-job'
           ].include?(@cmd))
  puts "Unknown subcmd #{@cmd}"
  puts "use aerobio -h, for useage"
  exit(1)
end


def test_file (f, *msg)
  if File.exists?(f)
    true
  else
    if msg.empty?
      puts "Error: file '#{f}' does not exist"
    else
      puts msg
    end
    exit(1)
  end
end

def to_tmpfile (data, *name)
  content = if data.is_a?(Array) then data else [data] end
  time = Time.now
  nm = if name.length == 0 then "tmp" else name[0] end
  nm = [nm, time.hour, time.min, time.sec].join("-")
  tempfile = File.join(Dir.tmpdir, nm)
  tempFile = File.new(tempfile, "w")
  content.each do |x| tempFile.puts(x) end
  tempFile.close
  return tempfile
end




def remote_cmd_fileargs (upload_type, infile, *misc)
  server = if (@server) then @server else @default_server end
  base_url = server + "/htseq/cmd"

  if infile.is_a?(Array)
    infile = infile.map do |f| File.new(f) end
  else
    infile = File.new(infile)
  end

  ## RestClient.post (and get, ...) are nice sugar wrappers for
  ## execute/resource instances, but they do not support passing of
  ## connection options - timeout in particular!  So, we create a
  ## resource manually with the timeout options set and call post on
  ## the result.
  resource = RestClient::Resource.new(base_url,
                                      :timeout => 600, :open_timeout => 600)
  return resource.post({"upload-type" => upload_type,
                        :subtype => "remote",
                        :reqtype => "cmdline",
                        :user => ENV['LOGNAME'].downcase,
                        :host => ENV["HOST"],
                        :file => infile, ##File.new(infile),
                        :misc => misc,
                        :multipart => true ##NECESSARY when vec of files!
                       },
                       {:cookies => {:user => ENV['LOGNAME'].downcase}})
end


def remote_cmd (cmd, args, *misc)
  server = if (@server) then @server else @default_server end
  base_url = server + "/htseq/cmd?"
  ##args = args.insert(args.count-1, "eid")
  ##args = args.each_slice(2).to_a
  args = args.insert(0,cmd)
  args = args.insert(0,ENV["USER"])
  if (cmd == "compare")
    keys = ["user", "cmd", "compfile", "eid"]
  else
    keys = ["user", "cmd", "action", "eid"]
  end
  if (args.count != keys.count)
    keys = keys.insert(keys.count-1,"rep")
  end
  args = keys.zip(args)
  args = args.map do |x| x.join("=") end
  argstg = args.join("&")
  puts "argstg = #{argstg}"
  return RestClient.get(base_url+argstg, {:reqtype => "cmdline"})
end




def get_result (result)
  result = if result.is_a?(String) then JSON.parse(result.body) else result end
  if (result["stat"] != "success")
    [[result["stat"], "", "", []], result]
  else
    info = result["info"]
    info_len = info.length
    jstat = info[0]
    rstat = info[1]
    ##puts "JSTAT = #{jstat}"
    ##puts "RSTAT = #{rstat}"
    ##puts "info[2..] = #{info[2..info_len]}"
    msg_bits = info[2..info_len]
    return [[result["stat"], jstat, rstat, msg_bits], result]
  end
end


def good_result? (result_array)
  return result_array[0] == "success"
end

def good_jstat? (result_array)
  return result_array[1] == "done"
end

def good_rstat? (result_array)
  return result_array[2] == "good"
end


def report_result (header, result)
  if (header == "RUN")
    puts JSON.parse(result)
  else
    res_array, result = get_result(result)
    if (!good_result?(res_array))
      puts "Error - #{res_array[1..res_array.length]}, #{result}"
    else
      puts ""
      if (header != "")
        puts header
      end
      if (good_jstat?(res_array) and !good_rstat?(res_array))
        puts "ERROR:"
      end
      msg_bits = res_array.last
      puts msg_bits
    end
    res_array
  end
end


def create_jobid_file (jobid, content)
  jobidfile = @aerobioDir + "/job"+jobid
  jobidFile = File.new(jobidfile, "w")
  jobidFile.syswrite("#{jobid}\n")
  if (!content.empty?)
    jobidFile.syswrite(content.join("\n"))
  end
  jobidFile.close
  FileUtils.cp(jobidfile, @aerobioDir + "/last-job.txt")
  return jobidfile
end


def host_busy_time (use_type)
  server = @server
  results = @server_names.map do |name|
    @server = @servers[name]
    tmpfile = to_tmpfile(use_type)
    result = remote_cmd("busy-check", tmpfile)
    File.delete(tmpfile)
    [name, result]
  end
  @server = server
  return results.map do |name, result|
    res_arr, x_ = get_result(result)
    if good_result?(res_arr)
      [name, res_arr.last[0]]
    else
      [name, "Reported #{res_arr[0]}"]
    end
  end
end




def rmt_content_to_files (basedir, names, contents)
  both = [names, contents]
  both.transpose.each do |fname, lines|
      if (!lines.empty?)
        outFile = File.new(File.join(basedir, fname), "w")
        if outFile
          outFile.syswrite(lines)
          outFile.close
        else
          puts "Unable to open file #{fname}"
        end
      end
    end
end

def correct_sto_finish (file, info)
  ## it's amazing how verbose and convoluted this all is.  You
  ## could easily do this entire function in three lines of Clojure.
  basedir = File.dirname(file)
  filename = File.basename(file)
  puts "Finished sto correction for #{file}", "Results in #{basedir}"

  contents = info[1..info.length]
  suffixes = ["-new.sto", "-diffs.txt", "-bad.txt"]
  names = suffixes.map do |x| filename.sub(/\.sto/, x) end
  rmt_content_to_files(basedir, names, contents)
  if File.exist?(File.join(basedir, names[0]))
    File.rename(file, File.join(basedir, filename.sub(/\.sto/, "-old.sto")))
    File.rename(File.join(basedir, names[0]), file)
  end
end

def embl_to_nc_finish(file, info)
  basedir = File.dirname(file)
  filename = File.basename(file)
  filetype = "\.#{filename.split(".").last}"
  puts "Finished EMB to NC conversion for #{file}", "Results in #{basedir}"

  contents = info[1..info.length]
  suffixes = [["-NC", filetype].join(""), ["-bad", filetype].join("")]
  names = suffixes.map do |x| filename.sub(Regexp.new(filetype), x) end
  rmt_content_to_files(basedir, names, contents)
end

def check_finish (jobidfile, data, result)
  jobid, cmd, file = data
  #puts "Data ", data, jobid, cmd, file

  res_arr, _x = get_result(result)

  if (cmd == "run")
    report_result("", result)

  elsif (cmd == "run-config")
    report_result("", result)

  elsif (cmd == "correct-sto-coordinates")
    if (!good_jstat?(res_arr))
      report_result("", result)
    elsif (!good_rstat?(res_arr))
      report_result("ERROR:", result)
    else
      correct_sto_finish(file, res_arr.last)
    end

  elsif (cmd == "embl-to-nc")
    if (!good_jstat?(res_arr))
      report_result("", result)
    elsif (!good_rstat?(res_arr))
      report_result("ERROR:", result)
    else
      embl_to_nc_finish(file, res_arr.last)
    end
  end

  if (good_jstat?(res_arr) and File.exist?(jobidfile))
    filename = File.basename(jobidfile)
    File.rename(jobidfile, File.join(@doneDir, filename))
  end
end


def check_job (args)
  if (args == [])
    jobidfile = File.join(@aerobioDir, "last-job.txt")
    test_file(jobidfile, "Error - you have no jobs")
  else
    jobid = args[0]
    jobidfile = "job"+jobid
    jobidfile = File.join(@aerobioDir, jobidfile)
  end
  if test_file(jobidfile, "No job with id #{jobid} found")
    data = IO.readlines(jobidfile).map do |x| x.chomp end
    jobid = data[0]
    tempfile = to_tmpfile(jobid, jobid)
    #tempfile = File.join(Dir.tmpdir, jobid)
    #tempFile = File.new(tempfile, "w")
    #tempFile.puts(jobid)
    #tempFile.close
    result = remote_cmd(@cmd, tempfile)
    File.delete(tempfile)
    ## Make sure we pass in the actual jobidfile (not default last-job.txt!!)
    jobidfile = File.join(@aerobioDir, "job"+jobid)
    check_finish(jobidfile, data, result)
  end
end

def check_jobs(jobids)
  jobids.each do |x|
    check_job([x])
  end
end




def correct_sto (args)
  args.each do |infile|
    infile = File.expand_path(infile)
    result = remote_cmd(@cmd, infile)
    result = report_result("", result)
    jobid = result.last[1]
    jobidfile = create_jobid_file("#{jobid}", [@cmd, infile])
  end
end


def embl_to_nc (args)
  infile = File.expand_path(args[0])
  result = remote_cmd(@cmd, infile)
  result = report_result("", result)
  jobid = result.last[1]
  jobidfile = create_jobid_file("#{jobid}", [@cmd, infile])
end


def run_job (args)
  puts "RUN args are: #{args}"
  ##tempfile = to_tmpfile(args)
  ##result = remote_cmd(@cmd, tempfile)
  ##File.delete(tempfile)
  ## Make sure we pass in the actual jobidfile (not default last-job.txt!!)
  ##jobidfile = File.join(@aerobioDir, "job"+jobid)
  result = remote_cmd(@cmd, args)
  report_result("RUN", result)
  ##jobid = result.last[1]
  ##jobidfile = create_jobid_file("#{jobid}", [@cmd, config_file])
end


def write_default_server (name)
  server_file = File.new(@default_server_file, "w")
  server_file.puts(name)
  server_file.close
end

def show_default_server ()
  if @default_server
    puts "Current default server: #{@default_server}"
  else
    puts "You have not set a default server."
    puts "Use 'aerobio server <name>', to set default to <name>."
    puts "Use 'aerobio server available', to show available names."
  end
end

def show_available_servers ()
  puts "Available servers:"
  @server_names.each do |x|
    puts "  #{x} --> #{@servers[x]}"
  end
end

def show_server_activity ()
  host_busy_time("idle").each do |name, result|
      res = if (result == "error") then "an error" else "#{result}% idle" end
      puts "#{name} reports #{res}"
    end
end

def set_default_server (name)
  if (name == "any")
    # HACK!, need to query and pick most idle
    @default_server = @servers[@server_names.last]
  elsif (@server_names.index(name))
    @default_server = @servers[name]
  else
    puts "Unknown server"
    puts "Use 'aerobio server available' to get available server list"
    exit(1)
  end
  write_default_server(name)
end

def server_options (args)
  if ((args.length == 0) or (args[0] == "show"))
    show_default_server()
  elsif (args[0] == "available")
    show_available_servers()
  elsif (args[0] == "activity")
    show_server_activity()
  else
    set_default_server(args[0])
  end
end


def list_jobs (state)
  dir = if (state == "active") then @aerobioDir else @doneDir end
  curjobs = Dir.glob(File.join(dir, "job*"))
  jstate = if (state == "active") then "running" else "finished" end
  if (curjobs.length == 0)
    puts "You have no #{jstate} jobs..."
  else
    curjobs.each do |jobfile|
      jobid, cmd, file = IO.readlines(jobfile).map do |x| x.chomp end
      puts "Job #{jobid}, #{jstate} command #{cmd} on #{file}"
    end
  end
end


def get_seqs (args)
  infile = File.expand_path(args[0])
  ld = if (args.length > 1) then args[1] else 0 end
  rd = if (args.length > 2) then args[2] else 0 end
  result = remote_cmd(@cmd, infile, [ld, rd])
  result = JSON.parse(result.body)
  if (result["stat"] == "success")
    info = result["info"]
    basedir = File.dirname(infile)
    filename = File.basename(infile)
    newfilename = "new-#{filename}"
    newfullspec = File.join(basedir, newfilename)
    outFile = File.new(newfullspec, "w")
    if outFile
      outFile.syswrite(info[info.length-1])
      outFile.close
      puts "New/Adjusted entries/seqs in #{newfullspec}"
    else
      puts "Unable to open file #{newfilename}"
    end
  else
    report_result("ERROR:", result)
  end
end


def entry_file_set(args)
  wsqs = (args[1] == "-with-seqs")
  names = (args[1] == "-names")
  fuzzy = (args[1] == "-fuzzy")
  modifier = (wsqs or fuzzy or names) ? args[1] : "-none"
  modifier = modifier.slice(1, modifier.length)
  modifierQ = (modifier != "none")

  if ((modifierQ and args.length < 4) or (not modifierQ and args.length < 3))
    puts "entry-file-set needs at least an operation and two input files"
    exit(1)
  end
  if !(['intersect', 'difference', 'union'].include?(args[0]))
    puts "entry-file-set operation #{args[0]} must be one of:"
    puts "  'intersect', 'difference', 'union'"
    exit(1)
  end
  op = args[0]
  delta = (fuzzy and args[2] =~ /\A\d+\z/) ? args[2] : nil

  files = modifierQ ? args[2..args.length] : args[1..args.length]
  files = delta ? files[1..files.length] : files
  delta = delta ? delta : "10"

  result = remote_cmd(@cmd, files, op, modifier, delta)
  result = JSON.parse(result.body)

  if (result["stat"] != "success")
    report_result("ERROR:", result)
  else
    info = result["info"][2] # first two are not used remote job status
    info = wsqs ? info.sort{|x,y| x[0] <=> y[0]} : info.sort
    basedir = File.dirname(files[0])
    namebits = File.basename(files[0]).split(".")
    filename, ext = [namebits.first, namebits.last]
    ext = (not wsqs) ? "ent" : ext
    newfilename = "#{filename}-#{op.upcase}.#{ext}"
    newfullspec = File.join(basedir, newfilename)
    outFile = File.new(newfullspec, "w")
    sto = (ext == "sto")
    if (!outFile)
      puts "Unable to open file #{newfullspec}"
    else
      info.each do |x|
        if wsqs
          ent, sq = x
          if sto
            outFile.printf("%-35s %-1s\n" % x)
          else
            outFile.printf(">#{ent}\n")
            outFile.printf("#{sq}\n")
          end
        else
          outFile.printf("#{x}\n")
        end
      end
      outFile.close
      puts "#{op.capitalize} output in #{newfullspec}"
    end
  end
end


def load_new_rna (file)
  result = remote_cmd(@cmd, file)
  res_arr, res = get_result(result)
  if (!good_result?(res_arr))
    report_result("", result)
  else
    added, xrefcnt = res_arr.last[0]
    puts "For #{file},\n"
    if (added.length > 0)
      puts "Added new RNAs:"
      added.each do |x| puts "  #{x}" end
      puts "With #{xrefcnt} new genome xrefs"
    else
      puts "No new RNAs added"
    end
  end
end

def load_new_rnas (args)
  args.each do |f| load_new_rna(f); puts "" end
end


def rna_taxon_info (args)
  i = 0
  outname = args[i]

  if (args[i += 1] == "-f")
    if test_file(args[i += 1])
      rnas = IO.readlines(args[i])
    end
  else
    rnas = args[i].split(/ *, */)
  end
  rnas = rnas.map do |r| bits = r.split(/( |\.|-)/); [bits[0], bits[2]] end

  if (args[i += 1] == "-f")
    if test_file(args[i += 1])
      taxons = IO.readlines(args[i])
    end
  else
    taxons = args[i].split(/ *, */)
  end

  time = Time.now
  tempfile = File.join(Dir.tmpdir,
                       ["rnatxinfo", time.hour, time.min, time.sec].join("-"))
  tempFile = File.new(tempfile, "w")
  rnas.each do |(r, v)| tempFile.puts("#{r}-#{v}") end
  tempFile.puts(";;;")
  taxons.each do |t| tempFile.puts(t) end
  tempFile.close
  result = remote_cmd(@cmd, tempfile)
  result = JSON.parse(result.body)
  File.delete(tempfile)

  if (result["stat"] != "success")
    report_result("ERROR:", result)
  else
    outFile = File.new(outname, "w")
    if (!outFile)
      puts "Unable to open file #{outname}"
    else
      info = result["info"][2] # first two are not used remote job status
      outFile.syswrite(info)
      outFile.close
      puts "Taxon group information in #{outname}"
    end
  end
end



def display_help (cmd)
  puts ""
  case cmd
  when "server"
    puts "server {<name> | 'any' | 'show' | 'activity' | 'available'}"
    puts ""
    puts "The server cmd enables you to specify a default server (by NAME),"
    puts "SHOW the currently set default server, show the ACTIVITY of the"
    puts "available servers (as percent idle across all cores), and list the"
    puts "set of AVAILABLE servers."
    puts ""
    puts "A default server setting will implicitly send all subsequent command"
    puts "requests to that server (until explicitly changed).  For any single"
    puts "request, you can explicitly specify a different server than the"
    puts "default, but prefacing the subcmd with an available server name."
    puts "See below for some examples of these uses.  If you do not set a"
    puts "default server and do not specifiy an explicit server for a command,"
    puts "an attempt is made to pick a server, but this may yield a 'no sever'"
    puts "error for the command.  Setting the default server to 'any' will"
    puts "result in the selection of a server automatically per command."
    puts ""
    puts "Examples:"
    puts ""
    puts "aerobio server roz"
    puts "  will set your default server to prince."
    puts ""
    puts "aerobio run 160307_NS500751_0013_AHL7L3BGXX"
    puts "  Given previous example, this will run the corrector on roz"
    puts ""
    puts "aerobio babs run rnaseq-phase0 160307_NS500751_0013_AHL7L3BGXX"
    puts "  Will run the job on babs irrespective of your default server"

  when "list"
    puts "list {['active' | 'done']}"
    puts ""
    puts "Lists your current ('active') jobs or your set of completed jobs."
    puts "With 'active' given, lists active/current running jobs."
    puts "With 'done' given, lists the completed jobs."
    puts "Default, when no argument is given, lists active jobs."

  when "check-sto"
    puts "check-sto <stofile>+"
    puts ""
    puts "Runs the aerobio sto file integrity checker.  Checks for a legally"
    puts "constructed and formatted file.  If the file is 'good', returns"
    puts "that as a message.  If there are problems, the problems are listed"
    puts "along with the genome names (N numbers) of the offending lines."

  when "get-seqs"
    puts "get-seqs [sto|aln|fna|ent] {[+|-]prefix [+|-]suffix}"
    puts ""
    puts "Takes a sto, aln, fasta, or 'entry' file and optional integers"
    puts "indicating how much to add or subtract to the start and end of"
    puts "sequences. Negative values always trim while positive values always"
    puts "add elements (nucleotides) to the sequence.\n"
    puts ""
    puts "Entry files are text files which have one entry per line, where an"
    puts "entry is <name/coordinates/strand>. 'name' is a genome name (only"
    puts "NC_* names currently).  'coordinates' are start-end, where start"
    puts "and end are integers, with end > start.  Strand is 1 or -1, and"
    puts "indicates the strand. For example: NC_013525/2 1770792-1770817/1\n"
    puts ""
    puts "get-seqs Supports two basic ways of getting or adjusting sequences.\n"
    puts ""
    puts "* For entry files and fasta files, just takes the entry coordinates,"
    puts "  adjusts them by any given prefix/suffix, and returns the sequence"
    puts "  for the updated coordinates (for entry name and strand) in fasta"
    puts "  format."
    puts ""
    puts "* For alignment files (sto and clustalw aln) the coordinates of"
    puts "  entries along with the given prefix/suffix are used to 'adjust'"
    puts "  the aligned (gapped) corresponding sequences.  For this to work"
    puts "  the coordinates _must_ correctly match exactly the degapped"
    puts "  version of the provided sequence."
    puts ""
    puts "  To ensure this is the case, you can use the correct-sto-coordinates"
    puts "  aerobio command to obtain a version of a sto file with each entry's"
    puts "  coordinates corrected to ensure a match with its given sequence."
    puts ""
    puts "  Gaps are treated as characters and counted in terms of trimming the"
    puts "  sequences (removal of characters from either end), but are not"
    puts "  counted in adjusting the corresponding entry's coordinates.  So,"
    puts "  the count of non gap characters in the trimmed subsequence is used"
    puts "  to compute the new coordinates that match the trimmed sequence."

  when "correct-sto-coordinates"
    puts "correct-sto-coordinates stofile+"
    puts ""
    puts "Takes a sto file, and for each entry/sequence pair, determines if the"
    puts "degapped sequence occurs _exactly_ in the genome given in the entry."
    puts "An entry is as defined for get-seqs.  Because correct-sto-coordinates"
    puts "may take considerable time (many minutes), it is always run as a job,"
    puts "and so will immediately return the job id for the run.  This may then"
    puts "be checked and results fetched by use of check-job.  Returns up to"
    puts "3 new files:"
    puts ""
    puts "* A new version of the original input sto file with the same name as"
    puts "  the original, with the original renamed with a trailing -old."
    puts ""
    puts "* A differences file, named off the original sto file name with a"
    puts "  trailing -diffs.  This contains an entry mapping from any entries"
    puts "  in the original to corresponding new versions as determined by the"
    puts "  true location and strand of the sequence in the genome."
    puts ""
    puts "* A 'bad' file, named off the original sto file name with a trailing"
    puts "  -bad.  This contains entries whose sequences could not be found in"
    puts "  the entry's given genome.  Each 'bad' entry has the original entry"
    puts "  (name/coordinates/strand), the original gapped sequence given for"
    puts "  the entry, the degapped version of this sequence, and the actual"
    puts "  sequence in the genome at the given coordinates."
    puts ""
    puts "If no differences or bad entries were found, the diffs and bad files"
    puts "will not exist, however there is always a new version file with the"
    puts "original renamed to old."

  when "embl-to-nc"
    puts "embl-to-nc embl-file"
    puts ""
    puts "Takes an input file with EMBL entry names and converts it to one"
    puts "with NCBI NC names.  Maintains sequence information.  EMBL-FILE is"
    puts "either a sto or fna file with entries named with EMBL accensions."
    puts "The result file name is the input name with '-NC' appended.  The"
    puts "result file is a corresponding sto or fna with the same sequence"
    puts "(and alignment if sto) with NCBI NC accensions replacing EMBL names."
    puts ""
    puts "Additionally, as there are organisms represented in various EMBL"
    puts "files, and some sequences which have dropped or added bases, a"
    puts "'bad' file is also produced with the sequences that could not be"
    puts "matched to a corresponding refseq genome."
    puts ""
    puts "Because embl-to-nc may take considerable time (many minutes), it is"
    puts "always run as a job, and so will immediately return the job id for"
    puts "the run.  This may then be checked and results fetched by use of"
    puts "check-job."

  when "entry-file-set"
    puts "entry-file-set op {modifier} file1 file2 & files"
    puts "               op -> ['intersect' | 'difference' | 'union']"
    puts "         modifier -> ['-with-seqs' | '-fuzzy' {delta} | '-names']"
    puts "            delta -> positive integer; defaults to 10 bases"
    puts ""
    puts "Performs the 'op' set operation on two or more sto, aln, fasta,"
    puts "or 'entry' files and writes the result to an output file in the same"
    puts "directory as input and with a filename that is the first input file"
    puts "name with '-<opname>' appended.  For example foo-intersect.ent"
    puts ""
    puts "If -fuzzy is given the operation is 'fuzzy': names and strand are"
    puts "matched exactly, but start and end coordinates are matched if they"
    puts "are within delta of cooresponding start and end coordinates."
    puts "***NOTE: Currently fuzzy matches only work on two files."
    puts ""
    puts "If -names is given, only the names of entries are compared.  Hence,"
    puts "all entries with any name matches are collapsed to the single name."
    puts "Can be considered as an 'extremely' fuzzy matching scheme."
    puts ""
    puts "If -with-seqs is given, the output also has the corresponding"
    puts "sequences for the result entry set.  In this case all input files"
    puts "must be of the same type (all stos, all fnas, etc.) and the file"
    puts "type of the output is the same (sto, fna, etc.)."
    puts "***NOTE: Currently for sto files, all # lines are removed!"
    puts ""
    puts "If -with-seqs is not given, the output is simply an entry file (a"
    puts ".ent file) with entries listed one per line."

  when "run"
    puts "run {<tool> | <func> | <job-flow>} {<args> | <args expid> | <expid>}"
    puts ""
    puts "Run a registered tool, function or job-flow definition with 'args'"
    puts "or 'args expid' or 'expid'. 'expid' is an experiment id, which is"
    puts "also the directory name of the sequencer output. 'args' is a space"
    puts "separated set ofoption arguments for the given tool, function, or"
    puts "job-flow."

  when "run-config"
    puts "run-config config-file"
    puts ""
    puts "Takes a job configuration (job config or simply config) file and"
    puts "submits it for running as a background job.  The config file is"
    puts "documented with config -h, and encompasses many different tools and"
    puts "options (such as cmbuild, cmcalibrate, cmsearch, csv-gen, et.al.)"
    puts "These are typically quite long running (hours to even days), and"
    puts "run-config returns immediately the job id for the job.  This can be"
    puts "checked, and results obtained with check-job."

  when "check-job"
    puts "check-job {all | jobid+}"
    puts ""
    puts "Checks on a job that was submitted for running in Aerobio in the"
    puts "background.  The job may have been explicitly submitted by run or"
    puts "run-config or implicitly via another command such as embl-to-nc."
    puts "Takes either the keyword 'all' or a list of one or more jobids (as"
    puts "returned from a job submitting command)"
    puts ""
    puts "For each requested job (or all current jobs if 'all' is given),"
    puts "check-job inquires of Aerobio the current status of the job.  If it is"
    puts "finished, it obtains the results of the job, otherwise reports the"
    puts "current running status of the job."

  when "load-new-rna"
    puts "load-new-rna <stofile>+"
    puts ""
    puts "Takes the information encoded in the stofiles and uses this to load"
    puts "new RNAs into the database.  This presumes that the stofiles adhere"
    puts "to the requirements for representing the information for a new RNA:"
    puts ""
    puts "* Name must have form rna_NUM-V, where NUM is a five digit integer"
    puts "  representing the ordinal number of the new RNA with as many '0'"
    puts "  as needed to make NUM five digits.  V is an integer version number."
    puts "  Example: rna_00006-3.sto"
    puts ""
    puts "* There are four (4) #=GF lines encoding the following:"
    puts "  #=GF RNA <symbol name of associated protein, e.g., rpsD>"
    puts "  #=GF Taxon <associate taxon branch NCBI Taxonomy ID>"
    puts "  #=GF in-vivo <verified associated organism NCBI Taxonomy ID>"
    puts "  #=GF in-vitro <verified associated organism NCBI Taxonomy ID>"

  when "rna-taxon-info"
    puts "rna-taxon-info outfile [rnas | -f rna-file] [taxons | -f taxon-file]"
    puts ""
    puts "Perform a 'new rna' taxon grouping information analysis on the given"
    puts "rnas and taxons and place resulting information in the given output"
    puts "file."
    puts ""
    puts "rnas can be given as either a string of comma separated rnas or by"
    puts "using the -f option, can be listed, one per line, in the given file."
    puts "In either case, an rna must be in the format rnaname[.|-| ]version."
    puts "rnaname is a Meyer lab convention name for new rnas, for example,"
    puts "rna_00011 or, if given with a cluster designation, rna_00011c3."
    puts "Version is a positive integer denoting the version of interest."
    puts "So, a full example could be rna_00011c1-2"
    puts ""
    puts "taxons can be given as either a string of comma separated taxon names"
    puts "or by using the -f option, can be listed one per line, in the given"
    puts "file.  The set of default taxons can be denoted by the special name"
    puts "'default-taxons'."
    puts ""
    puts "Examples:"
    puts "aerobio rna-taxon-info rna_11-2-txinfo.txt rna_00011.2 default-taxons"
    puts "aerobio rna-taxon-info taxon-info.txt -f rnas.txt -f taxons.txt"
    puts ""
    puts "Output format:"
    puts "rna-name, clu, ver, taxon-name, rna cnt in taxon, total cnt, percent"
    puts "list of genomes (by NC name)"
  end
  puts ""
end




if ((args.length > 0) and (["-h", "--help"].include?(args[0])))
    display_help(@cmd)

elsif (@cmd == "server")
  server_options(args)

elsif (@cmd == "list")
  if ((args.length == 0) or (["active", "running"].include?(args[0])))
    list_jobs("active")
  elsif (["done", "finished"].include?(args[0]))
    list_jobs("done")
  else
    puts "Unknown list request '#{args[0]}'"
  end

elsif (@cmd == "check-sto")
  args.each do |infile|
    infile = File.expand_path(infile)
    result = remote_cmd(@cmd, infile)
    report_result(infile, result)
  end

elsif (@cmd == "get-seqs")
  if (args.length == 0)
    puts "#{@cmd} requires at least a file."
    exit(1)
  end
  get_seqs(args)

elsif (@cmd == "correct-sto-coordinates")
  correct_sto(args)

elsif (@cmd == "embl-to-nc")
  embl_to_nc(args)

elsif (@cmd == "entry-file-set")
  entry_file_set(args)

elsif (@cmd == "load-new-rna")
  load_new_rnas(args)

elsif (@cmd == "rna-taxon-info")
  if (args.length < 3)
    puts "#{@cmd} requires outfile, rnas and taxons,"
    puts "Use #{@cmd} -h for details."
    exit(1)
  end
  rna_taxon_info(args)

elsif (@cmd == "check-job")
  if ((args.length != 0) and (args[0] == "all"))
    curjobs = Dir.glob(File.join(@aerobioDir, "job*"))
    if (curjobs.length == 0)
      puts "You have no active jobs..."
    else
      jobids = curjobs.map do |x| IO.readlines(x)[0].chomp end
      check_jobs(jobids)
    end
  elsif (args.length > 1)
    check_jobs(args)
  else
    check_job(args)
  end

elsif (@cmd == "run")
  if (args.length == 0)
    puts "#{@cmd} requires at least an experiment id. Use aerobio run -h for"
    puts "details on how to use run"
    exit(1)
  end
  run_job(@args[1..args.length])

elsif (@cmd == "compare")
  if (args.length <= 1)
    puts "#{@cmd} requires at least an experiment id. Use aerobio run -h for"
    puts "details on how to use compare"
    exit(1)
  end
  run_job(@args[1..args.length])

elsif (args.length > 1)
  puts "#{@cmd} has only single config file parameter, not #{args.length}"
  exit(1)
else
  config_file = File.expand_path(args[0])
  run_job(config_file)
end
