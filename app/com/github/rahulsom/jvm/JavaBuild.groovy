package com.github.rahulsom.jvm

import groovy.transform.Memoized
import groovy.transform.ToString
import groovy.util.logging.Log

/**
 * Represents a build of Java
 *
 * @author Rahul Somasunderam
 */
@ToString
@Log
class JavaBuild implements Serializable {

  static final long serialVersionUID = 1L;

  String title, size, filePath, key, majorVersion

  Set<String> getTags() {
    def tagEvaluators = [
        jre    : filePath.contains('jre') || title.contains('Runtime'),
        jdk    : filePath.contains('jdk') && !filePath.contains('jre') && !filePath.contains('doc'),
        sjre   : key.contains('sjre') || filePath.contains('server-jre'),

        macos  : filePath.endsWith('.dmg'),
        win    : filePath.endsWith('.exe'),
        rpm    : filePath.endsWith('.rpm') || filePath.endsWith('rpm.bin'),
        linux  : (title.toLowerCase().contains('linux') || filePath.contains('linux')) && !filePath.contains('rpm'),
        solaris: title.toLowerCase().contains('solaris') || filePath.contains('solaris'),

        arm32  : filePath.contains('arm-') || filePath.contains('arm32-'),
        arm64  : filePath.contains('arm64-'),
        x64    : filePath.contains('x64'),
        i586   : filePath.contains('i586'),
        sparcv9: filePath.contains('sparcv9'),
    ]
    def retval = tagEvaluators.findAll { k, v -> v }.keySet()
    if (retval.contains('jre') && retval.contains('sjre')) {
      retval -= 'jre'
    }
    def otherSetting = [
        othertype: ['jdk', 'jre', 'sjre'],
        otheros  : ['macos', 'win', 'rpm', 'linux', 'solaris'],
        otherarch: ['arm32', 'arm64', 'x64', 'i586', 'sparcv9'],
    ]
    otherSetting.each { k, v ->
      if (!retval.toList().intersect(v as Iterable)) {
        retval += k
      }
    }
    retval
  }

  String getVersion() {
    return computeVersion(key)
  }

  @Memoized
  private static String computeVersion(String key) {
    switch (key) {
      case ~/(\d+-)?(jdk|jre|sjre|j2sdk|j2re)-1\.((\d+)(\.\d+)?)B?(_(\d+[^-]+))?-.*/:
        def m = key =~ /(\d+-)?(jdk|jre|sjre|j2sdk|j2re)-1\.((\d+)(\.\d+)?)B?(_(\d+[^-]+))?-.*/
        def major = (m[0].size() < 5 || m[0][5] == null || m[0][5] == '.0') ? m[0][4] : m[0][3]
        if ((m[0].size() > 6 && m[0][7])) {
          def minor = m[0][7]
          if (minor.length() > 1 && minor[0] == '0') {
            minor = minor.replaceAll('^0+', '')
          }
          return "${major}u${minor}"
        } else {
          return "${major}u0"
        }
      case ~/(JRE|JDK|J2RE|J2SDK|JRE..|JDK..)-1(\d)(\d)(_(\d+))?-.*/:
        def m = key =~ /(JRE|JDK|J2RE|J2SDK|JRE..|JDK..)-1(\d)(\d)(_(\d+))?-.*/
        def major = m[0][2]
        def minor = m[0][3] == '0' ? '' : m[0][3]
        def build = m[0][5] ?: '0'
        build = build.replaceAll('^0+', '') ?: '0'
        return "${major}${minor ? '.' : ''}${minor}u${build}"
      case ~/(jdk|jre|sjre)-(\d+)(u(\d+))?-.*/:
        def m = key =~ /(jdk|jre|sjre)-(\d+)(u(\d+))?-.*/
        return (m[0].size() > 4 && m[0][4]) ? "${m[0][2]}u${m[0][4]}" : "${m[0][2]}u0"
      default:
        return "-1"
    }
  }
}
