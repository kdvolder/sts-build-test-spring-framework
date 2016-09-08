sts-build-test-spring-framework
===============================

An 'sts-build-test' from spring-framework. 

Run this build as follows:

     mvn clean integration-test
     
It will use tycho to provision a runtime environment to run a
single Eclipse JUnit plugin test. This test in turn will

 - download a spring-framework source zip from github
 - unpack this zip.
 - import it into Eclipse/STS.
 - Run eclipse workspace build process.
 - Examine the projects in the workspace for errors.
 
The test passes at the end of this process it finds no
errors in the workspace.