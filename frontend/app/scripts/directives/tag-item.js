(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('tagItem', function(TaxonomyCacheSrv) {
        return {
            restrict: 'E',
            replace: true,
            scope: {
                value: '=',
                colour: '='
            },
            templateUrl: 'views/directives/tag-item.html',
            link: function(scope/*, element, attrs*/) {
                if(!scope.value) {
                    return;
                }
                if(_.isString(scope.value)) {
                    scope.tag = scope.value;
                    scope.bgColor = scope.colour ||
                        TaxonomyCacheSrv.getColour(scope.value) ||
                        TaxonomyCacheSrv.getColour('_freetags_:' + scope.value) ||
                        '#3c8dbc';
                } else {
                    scope.tag = _.without([
                        scope.value.namespace,
                        ':',
                        scope.value.predicate,
                        scope.value.value ? ("=\"" + scope.value.value + "\"") : null
                    ], null).join('');
                    scope.bgColor = scope.value.colour || scope.colour || '#3c8dbc';
                }

                scope.$watch('colour', function(value) {
                    if(!value) {
                        return;
                    }
                    scope.bgColor = value;
                });
            }
        };
    });

})();
