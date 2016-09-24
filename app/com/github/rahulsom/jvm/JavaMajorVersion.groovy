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
  static final long serialVersionUID = 1L;
  String version, link
  List<JavaReleaseVersion> versions
}
