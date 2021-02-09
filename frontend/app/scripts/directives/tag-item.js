(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('tagItem', function(TaxonomyCacheSrv) {
        return {
            restrict: 'E',
            replace: true,
            scope: {
                value: '='
            },
            templateUrl: 'views/directives/tag-item.html',
            link: function(scope/*, element, attrs*/) {
                if(!scope.value) {
                    return;
                }
                if(_.isString(scope.value)) {
                    scope.tag = scope.value;
                    scope.bgColor = TaxonomyCacheSrv.getColour(scope.value) || '#3c8dbc';
                } else {
                    scope.tag = _.without([
                        scope.value.namespace,
                        ':',
                        scope.value.predicate,
                        scope.value.value ? ("=\"" + scope.value.value + "\"") : null
                    ], null).join('');
                    scope.bgColor = scope.value.colour || '#3c8dbc';
                }
            }
        };
    });

})();
