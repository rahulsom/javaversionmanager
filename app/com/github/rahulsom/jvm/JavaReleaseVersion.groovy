package com.github.rahulsom.jvm

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Represents Released Versions of Java. e.g. Java SE 8u92
 *
 * @author Rahul Somasunderam
 */
@ToString
@CompileStatic
class JavaReleaseVersion implements Serializable {
  static final long serialVersionUID = 1L;
  String key, versionTitle
  List<JavaBuild> builds = []
}
