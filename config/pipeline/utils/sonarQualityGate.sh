#!/bin/bash
#Fetching TASKURL from report-task.txt in workspace
sleep 10
url="http://sonarqube.awsdataservnp.manheim.com:9000"
taskUrl=$(cat $WORKSPACE/.scannerwork/report-task.txt | grep ceTaskUrl | cut -c11- )

#Fetching Task attributes from Sonar Server
curl -L $taskUrl | python -m json.tool

#Setting up task status to check if sonar scan is completed successfully.
curl -L $taskUrl -o task.json

status=$(python -m json.tool < task.json | grep -i "status" | cut -c20- | sed 's/.\(.\)$/\1/'| sed 's/.$//' )
echo ${status}

#If SonarScan is completed successfully then set analysis ID & URLS.
if [ $status = SUCCESS ]; then
analysisID=$(python -m json.tool < task.json | grep -i "analysisId" | cut -c24- | sed 's/.\(.\)$/\1/'| sed 's/.$//')
analysisUrl="${url}/api/qualitygates/project_status?analysisId=${analysisID}"
echo ${analysisID}
echo ${analysisUrl}

else
echo "Sonar run was unsuccessful"
exit 1

fi

#Fetching SonarGate details using analysis URL
curl ${analysisUrl} | python -m json.tool
curl ${analysisUrl} | python -m json.tool | grep -i "status" | cut -c28- | sed 's/.$//' >> tmp.txt
cat tmp.txt
sed -n '/ERROR/p' tmp.txt >> error.txt
cat error.txt

if [ $(cat error.txt | wc -l) -eq 0 ]; then
echo "Quality Gate Passed ! Setting up SonarQube Job Status to Success ! "
else
> error.txt
> tmp.txt
exit 1
echo "Quality Gate Failed ! Setting up SonarQube Job Status to Failure ! "
fi

#Cleaning up the files
unset url
unset taskUrl
unset status
unset analysisID
unset analysisUrl
> task.json
> tmp.txt
> error.txt