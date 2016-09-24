package com.github.rahulsom.jvm

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by rahul on 9/24/16.
 */
class VersionParsingSpec extends Specification {

  @Unroll
  def "#key should be parsed as #expected"() {
    expect:
    new JavaBuild(key: key).version == expected

    where:
    key                             | expected
    'jdk-8u92-oth-JPR'              | '8u92'
    'jre-8u92-oth-JPR'              | '8u92'
    'sjre-8u92-oth-JPR'             | '8u92'
    'jdk-6u25-javafx-1.3.1-oth-JPR' | '6u25'
    'jre-1.5.0_22-oth-JPR'          | '5u22'
  }
}
