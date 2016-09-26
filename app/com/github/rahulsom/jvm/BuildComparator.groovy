package com.github.rahulsom.jvm

/**
 * Comparator for JavaBuild
 *
 * @author Rahul Somasunderam
 */
class BuildComparator implements Comparator<JavaBuild> {

  @Override
  int compare(JavaBuild a, JavaBuild b) {
    def (_a, aMajor, aType, aMinor) = (a.version =~ /(\d+)([a-z]+)(\d+)/)[0]
    def (_b, bMajor, bType, bMinor) = (b.version =~ /(\d+)([a-z]+)(\d+)/)[0]
    Double.parseDouble(aMajor) <=> Double.parseDouble(bMajor) ?:
        aType <=> bType ?:
            Integer.parseInt(aMinor) <=> Integer.parseInt(bMinor)
  }

}