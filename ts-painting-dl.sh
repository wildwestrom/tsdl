#!/bin/sh

function ProgressBar {
# Process data
    let _progress=(${1}*100/${2}*100)/100
    let _done=(${_progress}*4)/10
    let _left=40-$_done
# Build progressbar string lengths
    _fill=$(printf "%${_done}s")
    _empty=$(printf "%${_left}s")

# 1.2 Build progressbar strings and print the ProgressBar line
# 1.2.1 Output example:
# 1.2.1.1 Progress : [########################################] 100%
printf "\rProgress : [${_fill// /#}${_empty// /-}] ${_progress}%%"
}

#Check for dependencies.
function DependencyCheck() {
    echo "Checking dependencies... "
    for prog in lynx curl wget awk sed
    do
      [[ $(command -v $prog 2>&1) ]] || { printf "\nDependency Missing: $prog needs to be installed. $prog'";deps=1; }
    done
    [[ $deps -ne 1 ]] && printf "OK\n" || { printf "\nInstall the above and rerun this script\n";exit 1; }
}

DependencyCheck

tempdir=$(mktemp -d)

function cleanup {
    rm -f $tempdir/cookies
    rm -f $tempdir/img-pages.txt
    rm -f $tempdir/HQ-img-links.txt
    rm -f $tempdir/lynx.cfg
}

function trap_ctrlc ()
{
    cleanup 
    echo "\n SIGINT detected; Stopping program."
    exit 2
}
 
trap "trap_ctrlc" 2

##Prompts user for login to TS Premium
#read -p    "Input Username/Email " USER
#read -s -p "Input Password "       PASS
#echo '\n'

printf "SET_COOKIES:TRUE\nACCEPT_ALL_COOKIES:TRUE\nPERSISTENT_COOKIES:TRUE\nCOOKIE_FILE:$tempdir/cookies" > $tempdir/lynx.cfg
lynx -cfg=$tempdir/lynx.cfg -post_data "auth=$USER&password=$PASS"--- https://tealswan.com/login

#curl -s -c $tempdir/cookies -d "auth=$USER&password=$PASS" -D $tempdir/headerdump -L https://tealswan.com/login/ | less

echo '\n'

# Gets links to the pages with the image download link.
PAGES=`curl -s -b $tempdir/cookies https://tealswan.com/paintings/ | grep -m 1 "Page 1 of" | grep -Eo '[0-9] +' | awk 'NR==2'`

for (( i = 1; i <= $PAGES; i++ )); do
  curl -s -b $tempdir/cookie.file https://tealswan.com/paintings/page/$i | grep "background-image" | awk 'BEGIN{
RS="</a>"
IGNORECASE=1
}
{
  for(o=1;o<=NF;o++){
    if ( $o ~ /href/){
      gsub(/.*href=\042/,"",$o)
      gsub(/\042.*/,"",$o)
      print $(o)
    }
  }
}' >> $tempdir/img-pages.txt
  ProgressBar ${i} ${PAGES}
done

TOTAL_IMAGES=`wc -l < $tempdir/img-pages.txt`

printf "\n$TOTAL_IMAGES paintings to be downloaded.\n"
# Gets the links to the desired images and names of images.
printf "\nDownloading links to images.\n"
COUNT=1
while read LINE
do
  curl -s -b $tempdir/cookies $LINE > $tempdir/PAGE

  LINK=$(grep -m 1 "download href=" < PAGE | awk 'BEGIN{
  RS="</a>"
  IGNORECASE=1
  }
  {
    for(o=1;o<=NF;o++){
      if ( $o ~ /href/){
        gsub(/.*href=\042/,"",$o)
        gsub(/\042.*/,"",$o)
        print $(o)
      }
    }
  }')

   NAME=$(grep "<title>" < PAGE \
   | sed -E 's/[[:space:]]+<title>//; s/ - Paintings.*>//; s/ /_/g')

   printf "$LINK;$NAME\n" >> $tempdir/HQ-img-links.txt

   ProgressBar ${COUNT} ${TOTAL_IMAGES}
   COUNT=`expr $COUNT + 1`

done < $tempdir/img-pages.txt

# Downloading the actual images.
printf "\nDownloading images.\n"
COUNT=1
while IFS=';' read -r LINK NAME
do
  wget -nc -O "$NAME.jpg" "$LINK" -q

  ProgressBar ${COUNT} ${TOTAL_IMAGES}
  COUNT=`expr $COUNT + 1`

done < $tempdir/HQ-img-links.txt

cleanup











