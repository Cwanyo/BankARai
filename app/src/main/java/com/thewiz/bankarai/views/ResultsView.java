package com.thewiz.bankarai.views;

import com.thewiz.bankarai.tfmodels.Classifier.Recognition;

import java.util.List;

/**
 * Created by C.wan_yo on 23-Feb-18.
 */

public interface ResultsView {

    void setResults(final List<Recognition> results);

}
