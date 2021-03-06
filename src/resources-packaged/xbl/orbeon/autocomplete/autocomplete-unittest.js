/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Autocomplete",

        /**
         * Runs the function for both the static and dynamic cases.
         *
         * @param f     function(staticDynamic, continuation)
         *              @param staticDynamic    Either "static" or "dynamic"
         *              @param continuation     Function with no parameter, but the caller needs to make sure
         *                                      to keep the this
         */
        runForStaticDynamic: function(f) {
            f.call(this, "static", function() {
                f.call(this, "dynamic", function(){});
            });
        },

        /**
         * Runs the function on all the active lis in the suggestion list.
         *
         * @param       function(li)
         *              @param li   Active list item
         */
        runOnLis: function(staticDynamic, f) {
            var autocompleteDiv = YAHOO.util.Dom.get(staticDynamic + "-autocomplete");
            var autocompleteSuggestions = YAHOO.util.Dom.getElementsByClassName("fr-autocomplete-yui-div", null, autocompleteDiv)[0];
            var lis = autocompleteSuggestions.getElementsByTagName("li");
            for (liIndex = 0; liIndex < lis.length; liIndex++) {
                var li = lis[liIndex];
                if (li.style.display != "none")
                    f.call(this, li);
            }
        },

        /**
         * Simulates the user typing a value in a search field.
         */
        simulateTypeInField: function(staticDynamic, newValue) {
            var searchInput = YAHOO.util.Dom.get(staticDynamic + "-autocomplete$search").getElementsByTagName("input")[0];
            searchInput.focus();
            searchInput.value = newValue;
            YAHOO.util.UserAction.keyup(searchInput);
        },

        simulateClickItem: function(staticDynamic, position) {
            var liIndex = 0;
            this.runOnLis(staticDynamic, function(li) {
                if (liIndex == position) {
                    YAHOO.util.UserAction.click(li);
                }
                liIndex++;
            });
        },

        /**
         * Checks that the external value is what we expect it to be.
         */
        checkExternalValue: function(staticDynamic, expectedValue, message) {
            var outputValue = ORBEON.xforms.Document.getValue(staticDynamic + "-output");
            YAHOO.util.Assert.areEqual(expectedValue, outputValue, staticDynamic +
                (YAHOO.lang.isUndefined(message) ? "" : " - " + message));
        },

        /**
         * Checks that the value in the search field is waht we expect it to be.
         */
        checkSearchValue: function(staticDynamic, expectedValue, message) {
            var searchValue = ORBEON.xforms.Document.getValue(staticDynamic + "-autocomplete$search");
            YAHOO.util.Assert.areEqual(expectedValue, searchValue, staticDynamic +
                (YAHOO.lang.isUndefined(message) ? "" : " - " + message));
        },

        /**
         * Checks that the items we get in the suggestion list are the one we expect.
         */
        checkSuggestions: function(staticDynamic, expectedValues) {
            this.runOnLis(staticDynamic, function(li) {
                YAHOO.util.Assert.areEqual(expectedValues[liIndex], li.innerHTML, staticDynamic + " at index " + liIndex);
            });
        },

        checkSuggestionCount: function(staticDynamic, expectedCount) {
            var actualCount = 0;
            this.runOnLis(staticDynamic, function(li) {
                actualCount++;
            });
            YAHOO.util.Assert.areEqual(actualCount, expectedCount, staticDynamic + " suggestions shows");
        },

        /**
         * This test needs to be first, as we test that setting the label to Canada on xforms-ready by dispatching
         * the fr-set-label event, we indeed get the value 'ca' in the node bound to the control.
         */
        testSetLabelOnXFormsReady: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                // Nothing is done in JS, but the fr-set-label we dispatch on xforms-ready will create some Ajax traffic
            }, function() {
                this.runForStaticDynamic(function(staticDynamic, continuation) {
                    this.checkExternalValue(staticDynamic, "ca");
                    continuation.call(this);
                });
            });
        },

        /**
         * Test that tabindex attribute is copied on the visible input field.
         */
        testHasTabIndex: function() {
            // On the static autocomplete we have tabindex="1"
            var staticVisibleInput = YAHOO.util.Dom.get("static-autocomplete").getElementsByTagName("input")[1];
            YAHOO.util.Assert.areEqual("1", ORBEON.util.Dom.getAttribute(staticVisibleInput, "tabindex"));
            // On the dynamic autocomplete we don't have a tabindex
            var dynamicVisibleInput = YAHOO.util.Dom.get("dynamic-autocomplete").getElementsByTagName("input")[1];
            var noTabindex = ORBEON.util.Dom.getAttribute(dynamicVisibleInput, "tabindex");
            // IE 6/7 returns 0, while other browsers returns null
            YAHOO.util.Assert.isTrue(noTabindex == null || noTabindex == 0);
        },

        /**
         * Test that when we type the full value "Switzerland", the value of the node becomes "sz",
         * because "Switzerland" shows in the list of possible values, so the value should be selected
         * even if it wasn't "clicked on" by the user.
         */
        testTypeFullValue: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    this.simulateTypeInField(staticDynamic, "Switzerland");
                }, function() {
                    this.checkExternalValue(staticDynamic, "sz");
                    continuation.call(this);
                });
            });
        },

        /**
         * Test that entering a partial match "Sw", we get the expected list of countries in the suggestion list.
         */
        testTypePartialValueSelect: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    this.simulateTypeInField(staticDynamic, "Sw");
                }, function() {
                    this.checkSuggestions(staticDynamic, ["Swaziland", "Sweden", "Switzerland"]);
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        this.simulateClickItem(staticDynamic, 1);
                    }, function() {
                        continuation.call(this);
                    });
                });
            });
        },

        testAlertShown: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // Set field to be empty
                    this.simulateTypeInField(staticDynamic, "");
                }, function() {
                    // Check the alert is active
                    var control = YAHOO.util.Dom.get(staticDynamic + "-autocomplete");
                    var container = YAHOO.util.Dom.getElementsByClassName("fr-autocomplete-container", null, control)[0];
                    YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(container, "xforms-invalid"));
                    var alert = YAHOO.util.Dom.getElementsByClassName("xforms-alert", null, control)[0];
                    YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(alert, "xforms-alert-active"),
                        "initially should have xforms-alert-active for " + staticDynamic);
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        // Set value to something that will start with the letter "s"
                        this.simulateTypeInField(staticDynamic, "Switzerland");
                    }, function() {
                        // Check the alert in inactive
                        YAHOO.util.Assert.isFalse(YAHOO.util.Dom.hasClass(container, "xforms-invalid"),
                            "after setting value, should not have xforms-invalid class for " + staticDynamic);
                        YAHOO.util.Assert.isFalse(YAHOO.util.Dom.hasClass(alert, "xforms-alert-active"),
                            "after setting value, should not have xforms-alert-active for " + staticDynamic);
                        continuation.call(this);
                    });
                });
            });
        },

        testDuplicateItemLabel: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // By typing the full country name, we already check that the suggestion comes and that it is
                    // not one of the 2 possibilities that is automatically selected
                    this.simulateTypeInField(staticDynamic, "United States");
                }, function() {
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        // Click on the first US
                        this.simulateClickItem(staticDynamic, 0);
                    }, function() {
                        this.checkExternalValue(staticDynamic, "us");
                        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                            this.simulateTypeInField(staticDynamic, "United States");
                        }, function() {
                            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                                // Click on the second US
                                this.simulateClickItem(staticDynamic, 1);
                            }, function() {
                                this.checkExternalValue(staticDynamic, "us2");
                                continuation.call(this);
                            });
                        });
                    });
                });
            });
        },

        testDoubleSpaceInLabel: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // By typing the full country name, we already check that the suggestion comes and that it is
                    // not one of the 2 possibilities that is automatically selected
                    this.simulateTypeInField(staticDynamic, "Virgin");
                }, function() {
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        // Click on the first Virgin Island
                        this.simulateClickItem(staticDynamic, 0);
                    }, function() {
                        this.checkExternalValue(staticDynamic, "vq", "1st value (vq) selected when clicking on the 1st item in the list");
                        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                            this.simulateTypeInField(staticDynamic, "Virgin");
                        }, function() {
                            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                                // Click on the second US
                                this.simulateClickItem(staticDynamic, 1);
                            }, function() {
                                this.checkExternalValue(staticDynamic, "vq2", "2nd value (vq2) selected when clicking on the 2nd item in the list");
                                this.checkSearchValue(staticDynamic, "Virgin  Islands");
                                continuation.call(this);
                            });
                        });
                    });
                });
            });
        },

        /**
         * The max-results-displayed is set to 4 in the markup with an attribute for the static case and an element
         * for the dynamic case.
         */
        testMaxResultsDisplayed: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // There are more than 4 countries that start with "Ba"
                    // Enter 2 letters, because the dynamic case requires 2 letters before it gives suggestions
                    this.simulateTypeInField(staticDynamic, "Ba");
                }, function() {
                    this.checkSuggestionCount(staticDynamic, 4);
                    // Select one of the items just to close the suggestion list
                    this.simulateClickItem(staticDynamic, 1);
                    continuation.call(this);
                });
            });
        },

        /**
         * Test that the left border of the suggestion box is aligned with the left border of the text field.
         */
        testAlignment: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // There are more than 4 countries that start with "Ba"
                    // Enter 2 letters, because the dynamic case requires 2 letters before it gives suggestions
                    this.simulateTypeInField(staticDynamic, "Ba");
                }, function() {
                    var container = YAHOO.util.Dom.get(staticDynamic + "-autocomplete");
                    var suggestions = YAHOO.util.Dom.getElementsByClassName("yui-ac-container", null, container)[0];
                    var input = YAHOO.util.Dom.getElementsByClassName("fr-autocomplete-search", null, container)[0];
                    YAHOO.util.Assert.areEqual(YAHOO.util.Dom.getX(suggestions), YAHOO.util.Dom.getX(input));
                    // Select one of the items just to close the suggestion list
                    this.simulateClickItem(staticDynamic, 1);
                    continuation.call(this);
                });
            });
        },

        testSetLabel: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get(staticDynamic + "-set-to-canada"));
                }, function() {
                    this.checkExternalValue(staticDynamic, "ca", "external value is 'ca' because Canada exists in the itemset");
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        YAHOO.util.UserAction.click(YAHOO.util.Dom.get(staticDynamic + "-set-to-utopia"));
                    }, function() {
                        this.checkExternalValue(staticDynamic, "", "external value is empty string because Utopia does not exist in the itemset");
                        continuation.call(this);
                    });
                });
            });
        },

        testSetFocus: function() {
            var staticAutocompleteInput = YAHOO.util.Dom.getElementsByClassName("yui-ac-input", null, "static-autocomplete")[0];
            var dynamicAutocompleteInput = YAHOO.util.Dom.getElementsByClassName("yui-ac-input", null, "dynamic-autocomplete")[0];
            var staticGotFocus = false;
            var dynamicGotFocus = false;
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.Event.addFocusListener(staticAutocompleteInput, function() { staticGotFocus = true; }, this, true);
                YAHOO.util.Event.addFocusListener(dynamicAutocompleteInput, function() { dynamicGotFocus = true; }, this, true);
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("static-setfocus"));
            }, function() {
                YAHOO.util.Assert.isTrue(staticGotFocus);
                YAHOO.util.Assert.isFalse(dynamicGotFocus);

                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get("dynamic-setfocus"));
                }, function() {
                    YAHOO.util.Assert.isTrue(staticGotFocus);
                    YAHOO.util.Assert.isTrue(dynamicGotFocus);
                });
            });
        },

        testValueForStatic: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("static-set-to-sz"));
            }, function() {
                this.checkSearchValue("static", "Switzerland");
            });
        },

        /**
         * Test that the full itemset dropdown can be used to make a selection in the static case.
         */
        testFullItemsetDropdown: function() {
            var autocompleteDiv = YAHOO.util.Dom.get("static-autocomplete");
            var select1ButtonDiv = YAHOO.util.Dom.getElementsByClassName("xbl-fr-select1-button", null, autocompleteDiv)[0];
            var select1ButtonButton = select1ButtonDiv.getElementsByTagName("button")[0];
            YAHOO.util.UserAction.click(select1ButtonButton);
            //select1ButtonButton.click();
            // This test is incomplete as we need to figure out a way to simulate a click action the button
            // that shows the drop-down.
        },

        EOO: {}
    }));

    if (parent && parent.TestManager) {
        parent.TestManager.load();
    } else {
        new YAHOO.tool.TestLogger();
        YAHOO.tool.TestRunner.run();
    }
});
