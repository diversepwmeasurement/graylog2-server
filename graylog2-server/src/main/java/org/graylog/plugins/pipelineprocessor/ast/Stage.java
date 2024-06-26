/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.plugins.pipelineprocessor.ast;

import com.codahale.metrics.Meter;
import com.google.auto.value.AutoValue;
import org.graylog.plugins.pipelineprocessor.processors.PipelineMetricRegistry;

import java.util.List;

@AutoValue
public abstract class Stage implements Comparable<Stage> {
    public enum Match {
        ALL, EITHER, PASS
    }

    private List<Rule> rules;
    // not an autovalue property, because it introduces a cycle in hashCode() and we have no way of excluding it
    private transient Pipeline pipeline;
    private transient Meter executed;

    public abstract int stage();

    public abstract Match match();

    public abstract List<String> ruleReferences();

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static Builder builder() {
        return new AutoValue_Stage.Builder();
    }

    public abstract Builder toBuilder();

    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") Stage other) {
        return Integer.compare(stage(), other.stage());
    }

    /**
     * Register the metrics attached to this stage.
     *
     * @param metricRegistry the registry to add the metrics to
     * @param pipelineId     the pipeline ID
     */
    public void registerMetrics(PipelineMetricRegistry metricRegistry, String pipelineId) {
        executed = metricRegistry.registerStageMeter(pipelineId, stage(), "executed");
    }

    public void markExecution() {
        if (executed != null) {
            executed.mark();
        }
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Stage build();

        public abstract Builder stage(int stageNumber);

        public abstract Builder match(Match match);

        public abstract Builder ruleReferences(List<String> ruleRefs);
    }

    @Override
    public String toString() {
        return "Stage " + stage();
    }
}
