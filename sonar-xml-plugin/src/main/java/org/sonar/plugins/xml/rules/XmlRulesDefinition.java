/*
 * SonarQube XML Plugin
 * Copyright (C) 2010-2017 SonarSource SA
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
package org.sonar.plugins.xml.rules;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Locale;
import javax.annotation.Nullable;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.plugins.xml.checks.CheckRepository;
import org.sonar.plugins.xml.language.Xml;
import org.sonar.squidbridge.annotations.AnnotationBasedRulesDefinition;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Repository for XML rules.
 */
public final class XmlRulesDefinition implements RulesDefinition {

  private final Gson gson = new Gson();

  @Override
  public void define(Context context) {
    NewRepository repository = context
      .createRepository(CheckRepository.REPOSITORY_KEY, Xml.KEY)
      .setName(CheckRepository.REPOSITORY_NAME);

    new AnnotationBasedRulesDefinition(repository, Xml.KEY).addRuleClasses(false, CheckRepository.getCheckClasses());

    for (NewRule rule : repository.rules()) {
      String metadataKey = rule.key();
      // Setting internal key is essential for rule templates (see SONAR-6162), and it is not done by AnnotationBasedRulesDefinition from
      // sslr-squid-bridge version 2.5.1:
      rule.setInternalKey(metadataKey);
      rule.setHtmlDescription(readRuleDefinitionResource(metadataKey + ".html"));
      addMetadata(rule, metadataKey);
    }

    repository.done();
  }

  @Nullable
  private static URL readRuleDefinitionResource(String fileName) {
    return XmlRulesDefinition.class.getResource("/org/sonar/l10n/xml/rules/xml/" + fileName);
  }

  private void addMetadata(NewRule rule, String metadataKey) {
    URL jsonUrl = readRuleDefinitionResource(metadataKey + ".json");
    if (jsonUrl != null) {
      try (Reader reader = new BufferedReader(new InputStreamReader(jsonUrl.openStream(), UTF_8))) {
        RuleMetadata metadata = gson.fromJson(reader, RuleMetadata.class);
        rule.setSeverity(metadata.defaultSeverity.toUpperCase(Locale.US));
        rule.setName(metadata.title);
        rule.setTags(metadata.tags);
        rule.setType(RuleType.valueOf(metadata.type));
        rule.setStatus(RuleStatus.valueOf(metadata.status.toUpperCase(Locale.US)));

        if (metadata.remediation != null) {
          // metadata.remediation is null for template rules
          rule.setDebtRemediationFunction(metadata.remediation.remediationFunction(rule.debtRemediationFunctions()));
          rule.setGapDescription(metadata.remediation.linearDesc);
        }
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read " + jsonUrl, e);
      }
    }
  }

  private static class RuleMetadata {
    String title;
    String status;
    String type;
    @Nullable
    Remediation remediation;
    String[] tags;
    String defaultSeverity;
  }

  private static class Remediation {
    String func;
    String constantCost;
    String linearDesc;
    String linearOffset;
    String linearFactor;

    private DebtRemediationFunction remediationFunction(DebtRemediationFunctions drf) {
      if (func.startsWith("Constant")) {
        return drf.constantPerIssue(constantCost.replace("mn", "min"));
      }
      if ("Linear".equals(func)) {
        return drf.linear(linearFactor.replace("mn", "min"));
      }
      return drf.linearWithOffset(linearFactor.replace("mn", "min"), linearOffset.replace("mn", "min"));
    }
  }

}
