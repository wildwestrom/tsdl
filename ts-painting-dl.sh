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

# echo -n "Checking dependencies... "
# for name in lynx curl wget grep awk sed
# do
#   [[ $(command -v $name >/dev/null 2>&1) ]] || { echo -en "\nDependency Missing: $name needs to be installed. $name'";deps=1; }
# done
# [[ $deps -ne 1 ]] && echo "OK" || { echo -en "\nInstall the above and rerun this script\n";exit 1; }

rm -f cookie.file
rm -f img-pages.txt
rm -f HQ-image-links.txt
rm -f lynx.cfg

#Prompts user for login to TS Premium
printf "SET_COOKIES:TRUE\nACCEPT_ALL_COOKIES:TRUE\nPERSISTENT_COOKIES:TRUE\nCOOKIE_FILE:cookie.file" > lynx.cfg
printf "Please log in to Teal Swan Premium.\n"
lynx -cfg=lynx.cfg https://tealswan.com/login
printf "\n"

# Gets links to the pages with the image download link.
PAGES=`curl -s -b cookie.file https://tealswan.com/paintings/ | grep -m 1 "Page 1 of" | grep -Eo '[0-9] +' | awk 'NR==2'`

for (( i = 1; i <= $PAGES; i++ )); do
  curl -s -b cookie.file https://tealswan.com/paintings/page/$i | grep "background-image" | awk 'BEGIN{
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
}' >> img-pages.txt
  ProgressBar ${i} ${PAGES}
done

TOTAL_IMAGES=`wc -l < img-pages.txt`

printf "\n$TOTAL_IMAGES paintings to be downloaded.\n"

# Gets the links to the desired images and names of images.
COUNT=1
while read LINE
do
  curl -s -b cookie.file $LINE > PAGE

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

   NAME=$(grep "<title>" < PAGE | sed -E 's/[[:space:]]+<title>//; s/ - Paintings.*>//; s/ /_/g')

   printf "$LINK;$NAME\n" >> HQ-img-links.txt

   ProgressBar ${COUNT} ${TOTAL_IMAGES}
   COUNT=`expr $COUNT + 1`
done < img-pages.txt
rm -f PAGE

# Downloading the actual images.
COUNT=1
while IFS=';' read -r LINK NAME
do
  wget -nc -O "$NAME.jpg" "$LINK" -q

  ProgressBar ${COUNT} ${TOTAL_IMAGES}
  COUNT=`expr $COUNT + 1`
done < HQ-img-links.txt

rm -f cookie.file
rm -f HQ-image-links.txt
rm -f img-pages.txt
rm -f lynx.cfg
