/*
 * Copyright (c) 2014 Houbrechts IT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.houbie.gradle.lesscss

import com.github.houbie.lesscss.LessParseException
import com.github.houbie.lesscss.Options
import com.github.houbie.lesscss.builder.CompilationUnit
import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class LesscTaskSpec extends Specification {
    File projectDir = new File('build/tmp/testProject')
    String projectRelativeLessDir = '../../../src/test/resources/less'
    File lessDir = new File('src/test/resources/less')
    Project project

    def setup() {
        projectDir.mkdirs()
        project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.apply plugin: 'lesscss'
        project.delete('out')
    }

    def 'lessc task defaults'() {
        def lesscTask = project.tasks.findByName('lessc')

        expect:
        lesscTask != null
        lesscTask.description == 'Compile LESS to CSS'
        lesscTask.options == new Options()
        lesscTask.engine == null
        lesscTask.lesscExecutable == null
        lesscTask.customJavaScript == null
        lesscTask.encoding == null
    }

    def 'configured lessc task'() {
        project.lessc {
            options.rootpath = 'myRootpath'
            engine = 'myEngine'
            lesscExecutable = 'myLesscExecutable'
            customJavaScript = 'myCustomJs'
            encoding = 'myEncoding'
            destinationDir = 'out'
        }

        def lesscTask = project.tasks.findByName('lessc')

        expect:
        lesscTask.options == new Options(rootpath: 'myRootpath')
        lesscTask.engine == 'myEngine'
        lesscTask.lesscExecutable == 'myLesscExecutable'
        lesscTask.customJavaScript == 'myCustomJs'
        lesscTask.encoding == 'myEncoding'
        lesscTask.destinationDir.absoluteFile == new File(projectDir, 'out').absoluteFile
    }

    def 'create custom lessc task'() {
        project.task(type: LesscTask, 'customLessc') {
            options.rootpath = 'customRootpath'
        }

        def customLesscTask = project.tasks.findByName('customLessc')

        expect:
        customLesscTask.options == new Options(rootpath: 'customRootpath')
        customLesscTask.engine == null
        customLesscTask.lesscExecutable == null
        customLesscTask.customJavaScript == null
        customLesscTask.encoding == null
    }

    @Unroll
    def 'compile less files in subdirectory for #engineName engine'() {
        when:
        project.lessc {
            destinationDir = project.file('out')
            sourceDir '../../../src/test/resources'
            include '**/import.less', '**/basic.less'
            include '**/*resource.*'
            engine = engineName
            lesscExecutable = System.getProperty('lesscExecutable', 'lessc')
        }

        project.tasks.findByName('lessc').run()

        then:
        new File(projectDir, 'out/less').list().sort() == ['basic-resource.txt', 'basic.css', 'import.css', 'import1'] as String[]
        new File(projectDir, 'out/less/basic.css').text == new File(lessDir, 'basic.css').text
        new File(projectDir, 'out/less/import.css').text == new File(lessDir, 'import.css').text

        where:
        engineName << ['rhino']
    }

    def 'compile less files'() {
        project.lessc {
            destinationDir = project.file('out')
            sourceDir projectRelativeLessDir
            include 'import.less', 'basic.less'
            include '*resource.*'
        }

        project.tasks.findByName('lessc').run()

        expect:
        new File(projectDir, 'out').list().sort() == ['basic-resource.txt', 'basic.css', 'import.css'] as String[]
        new File(projectDir, 'out/basic.css').text == new File(lessDir, 'basic.css').text
        new File(projectDir, 'out/import.css').text == new File(lessDir, 'import.css').text
    }

    def 'compile broken less'() {
        project.lessc {
            destinationDir = project.file('out')
            sourceDir = projectRelativeLessDir
            include 'broken.less'
        }

        when:
        project.tasks.findByName('lessc').run()

        then:
        LessParseException e = thrown()
        e.message == "less parse exception: missing closing `}`\n" +
                "in broken.less at line 1\n" +
                "extract\n" +
                "#broken less {"
    }

    def 'compile and minify'() {
        project.lessc {
            options.minify = true
            destinationDir = project.file('out')
            sourceDir = projectRelativeLessDir
            include 'minify.less'
        }

        when:
        project.tasks.findByName('lessc').run()

        then:
        new File(projectDir, 'out/minify.css').text == new File(lessDir, 'minify.css').text
    }

    def 'preCompile closure'() {
        project.lessc {
            destinationDir = project.file('out')
            sourceDir projectRelativeLessDir
            include 'import.less', 'basic.less'

            preCompile { FileTreeElement src, CompilationUnit unit ->
                unit.destination = project.file("precompile/${src.name}.css")
                unit.options.sourceMap = true
                unit.sourceMapFile = project.file("precompile/${src.name}.map")
            }
        }

        project.tasks.findByName('lessc').run()

        expect:
        new File(projectDir, 'precompile/basic.less.css').text == new File(lessDir, 'basic.css').text + "/*# sourceMappingURL=${project.file('precompile/basic.less.map')} */"
        new File(projectDir, 'precompile/import.less.css').text == new File(lessDir, 'import.css').text + "/*# sourceMappingURL=${project.file('precompile/import.less.map')} */"
//        new File(projectDir, 'precompile/basic.less.map').text == new File(lessDir, 'basic.map').text
//        new File(projectDir, 'precompile/import.less.map').text == new File(lessDir, 'import.map').text
    }
}
