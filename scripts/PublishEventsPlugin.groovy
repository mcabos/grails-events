includeTargets << grailsScript("_GrailsClean")
includeTargets << new File(releasePluginDir, "scripts/_GrailsMaven.groovy")

target(publishEventsPlugin: "Helper to publish plugin version") {
	depends(cleanAll, parseArguments, mavenInstall, mavenDeploy)
}

setDefaultTarget(publishEventsPlugin)
