/*
 * SonarTS
 * Copyright (C) 2017-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugin.typescript;

import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.internal.JUnitTempFolder;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.plugin.typescript.SensorContextUtils.AnalysisResponse;
import org.sonar.plugin.typescript.SensorContextUtils.ContextualAnalysisRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.plugin.typescript.TestUtils.BASE_DIR;
import static org.sonar.plugin.typescript.TestUtils.createInputFile;
import static org.sonar.plugin.typescript.TestUtils.TYPE_SCRIPT_RULES;

public class ContextualServerTest {

  @org.junit.Rule
  public JUnitTempFolder temp = new JUnitTempFolder();

  @Rule
  public final LogTester logTester = new LogTester();

  @Test
  public void should_start_and_stop() {
    ContextualServer contextualServer = getContextualServer();
    contextualServer.start();
    assertThat(contextualServer.isAlive()).isTrue();
    assertThat(logTester.logs()).contains("SonarTS Server is started");
    contextualServer.stop();
    assertThat(logTester.logs()).contains("SonarTS Server is stopped");
  }

  @Test
  public void should_log_warning_if_not_typescript_location_provided() {
    ContextualServer contextualServer = new ContextualServer(new MapSettings().asConfig(), mockTSServer(), temp);
    contextualServer.start();

    assertThat(logTester.logs(LoggerLevel.WARN))
      .containsOnlyOnce("No value provided by SonarLint for TypeScript location; property sonar.typescript.internal.typescriptLocation is missing");
    contextualServer.stop();
  }

  @Test
  public void should_not_start_or_stop_twice() {
    ContextualServer contextualServer = getContextualServer();
    contextualServer.start();
    contextualServer.start();

    assertThat(logTester.logs(LoggerLevel.WARN)).containsOnlyOnce("Skipping SonarTS Server start, already running");
    contextualServer.stop();
    contextualServer.stop();
    assertThat(logTester.logs(LoggerLevel.WARN)).containsOnlyOnce("SonarTS Server was already stopped");
  }

  @Test
  public void should_fail_when_not_started() {
    ContextualServer contextualServer = new ContextualServer(defaultConfiguration(), new TestBundleFactory().command(TestBundleFactory.getNodeExecutable(), "--version"), temp);
    contextualServer.start();
    assertThat(logTester.logs(LoggerLevel.ERROR)).containsOnlyOnce("Failed to start SonarTS Server");
    assertThat(contextualServer.isAlive()).isFalse();
  }

  @Test
  public void should_provide_analysis_results() throws Exception {
    ContextualServer contextualServer = getContextualServer();
    contextualServer.start();

    DefaultInputFile inputFile = createInputFile(SensorContextTester.create(BASE_DIR), "function foo() {}", "foo/file.ts");

    ContextualAnalysisRequest request = new ContextualAnalysisRequest(inputFile, TYPE_SCRIPT_RULES);
    AnalysisResponse analyze = contextualServer.analyze(request);

    assertThat(analyze.issues).hasSize(1);
    assertThat(analyze.cpdTokens).isEmpty();
  }

  private ContextualServer getContextualServer() {
    return new ContextualServer(defaultConfiguration(), mockTSServer(), temp);
  }

  private TestBundleFactory mockTSServer() {
    return TestBundleFactory.nodeScript("/mockSonarTSServer.js");
  }

  private Configuration defaultConfiguration() {
    MapSettings mapSettings = new MapSettings();
    mapSettings.setProperty("sonar.typescript.internal.typescriptLocation", "not used in tests");
    return mapSettings.asConfig();
  }
}
