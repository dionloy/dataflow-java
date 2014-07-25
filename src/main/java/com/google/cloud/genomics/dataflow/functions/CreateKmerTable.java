/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.genomics.dataflow.functions;

import com.google.api.client.util.Lists;
import com.google.cloud.dataflow.sdk.transforms.AsIterable;
import com.google.cloud.dataflow.sdk.transforms.FromIterable;
import com.google.cloud.dataflow.sdk.transforms.PTransform;
import com.google.cloud.dataflow.sdk.transforms.SeqDo;
import com.google.cloud.dataflow.sdk.transforms.SerializableFunction;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * Given a series of kmer count entries, collects them all and outputs the results to a csv table
 * 
 * Input: KV<KV<Name, Kmer>, Count>
 * Output: Each row of the table. First element a title row
 */
public class CreateKmerTable extends 
    PTransform<PCollection<KV<KV<String, String>, Long>>, PCollection<String>> {
  private static final Logger LOG = Logger.getLogger(CreateKmerTable.class.getName());
  
  /**
   * From a series of kmer counts generates a table
   */
  static class GenTable implements 
      SerializableFunction<Iterable<KV<KV<String, String>, Long>>, Iterable<String>> {
    
    @Override
    public Iterable<String> apply(Iterable<KV<KV<String, String>, Long>> similarityData) {
      // Transform the data into a matrix
      LinkedHashSet<String> accessions = new LinkedHashSet<String>();
      HashSet<String> kmers = new HashSet<String>();
      HashMap<String, Long> counts = new HashMap<String, Long>();

      LOG.info("Loading data into hash tables");
      for (KV<KV<String, String>, Long> entry : similarityData) {
        String accession = entry.getKey().getKey();
        String kmer = entry.getKey().getValue();
        accessions.add(accession);
        kmers.add(kmer);
        counts.put(accession + " " + kmer, entry.getValue());
      }
      LOG.info("Loaded " + accessions.size() + " rows and " + kmers.size() + " columns");
      
      List<String> table = Lists.newArrayList();

      LOG.info("Generating rows");
      StringBuilder title = new StringBuilder();
      title.append("Accessions");
      for (String kmer : kmers) {
        title.append(",").append(kmer);
      }
      table.add(title.toString());

      for (String accession : accessions) {
        StringBuilder line = new StringBuilder();
        line.append(accession);
        for (String kmer : kmers) {
          Long count = counts.get(accession + " " + kmer);
          line.append(",").append((count == null) ? 0 : count);
        }
        table.add(line.toString());
      }

      LOG.info("Completed table generation");
      return table;
    }
  }

  @Override
  public PCollection<String> apply(PCollection<KV<KV<String, String>, Long>> kmers) {
    return kmers.apply(AsIterable.<KV<KV<String, String>, Long>>create())
    .apply(SeqDo.named("Create table").of(new GenTable()))
    .apply(FromIterable.<String>create()).setOrdered(true);
  }
}
