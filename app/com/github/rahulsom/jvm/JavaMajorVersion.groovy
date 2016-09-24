package com.github.rahulsom.jvm

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Represents Major Versions of Java. e.g. Java SE 8
 *
 * @author Rahul Somasunderam
 */
@CompileStatic
@ToString
class JavaMajorVersion implements Serializable {
  String version
  String link
  List<JavaReleaseVersion> versions
}
