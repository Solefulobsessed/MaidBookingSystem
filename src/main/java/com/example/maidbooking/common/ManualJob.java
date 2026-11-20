package com.example.maidbooking.common;

/** A background job that can also be run on demand (demo and tests) through {@code POST /admin/run/{name}}. */
public interface ManualJob {

    /** The name used in the URL, e.g. {@code allocation}. */
    String name();

    /** Runs the job once now and returns what it did. */
    Object runNow();
}
