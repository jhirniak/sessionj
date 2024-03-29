#$ awk -f ../bin/parser.awk -v prefix='csv/bmark2' bmark2-....txt

##
# N.B. This script will overwrite existing files without warning.
# (Duplicated without changes from bmark1.)
##

BEGIN {
	if (prefix == "") {
		print "Usage: awk -f parser.awk -v prefix=[prefix] data_file"
		exit 1
	}

	file_base = prefix "-"
	file = ""
}

#e.g. Parameters: version=NOALIASm, size=0, length=1, repeat=0
/Parameters/ {
	split($2, tmp, "=")
	version = substr(tmp[2], 1, length(tmp[2]) - 1) # Remove comma separator
	split($3, tmp, "=")
	size =substr(tmp[2], 1, length(tmp[2]) - 1)
	split($4, tmp, "=")
	len =substr(tmp[2], 1, length(tmp[2]) - 1)
	split($5, tmp, "=")
	repeat = tmp[2]

	file = file_base version "-size_" size "-len_" len ".csv"

	if (repeat == "0") {
		first = "TRUE" # Entered a new parameter configuration
	}
}

#e.g. [TimerClient] Initialisation: 792844 nanos
/TimerClient/ {
	flag = substr($2, 1, length($2) - 1) # Remove colon separator
	time = $3
	unit = $4

	if (flag == "Body") { # FIXME: currently hardcoded to extract "body" values only
		if (first == "TRUE") {
			print $3 > file
			first = "FALSE"
		}
		else {
			print $3 >> file
		}
	}
}

