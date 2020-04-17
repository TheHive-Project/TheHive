(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('entityLink', function($compile, EntitySrv, $window) {
        return {
            compile: function(tElement, tAttr, transclude) {
                var contents = tElement.contents().remove();
                var compiledContents;
                return function(scope, iElement) {

                    scope.openEntity = EntitySrv.open;
                    scope.entityUrl = EntitySrv.link;
                    scope.openLink = function(link) {
                        if (scope.target) {
                            scope.target.location.href = link;
                        } else {
                            $window.location.href = link;
                        }
                    };

                    if (angular.isDefined(scope.value)) {
                        if (!compiledContents) {
                            // Get the link function with the contents from top
                            // level template with
                            // the transclude
                            compiledContents = $compile(contents, transclude);
                        }
                        // Call the link function to link the given scope and
                        // a Clone Attach Function,
                        // http://docs.angularjs.org./api/ng.$compile :
                        // "Calling the linking function returns the element of the
                        // template.
                        // It is either the original element passed in,
                        // or the clone of the element if the cloneAttachFn is
                        // provided."
                        compiledContents(scope, function(clone) {
                            // Appending the cloned template to the instance
                            // element, "iElement",
                            // on which the directive is to used.
                            iElement.append(clone);
                        });
                    }
                };
            },
            restrict: 'E',
            templateUrl: 'views/directives/entity-link.html',
            scope: {
                'value': '=',
                'target': '='
            }
        };
    });
})();
