(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('filterBox', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/directives/filter-box.html',
                scope: {
                    collection: '=',
                    query: '=?'
                },
                link: function(scope) {
                    scope.query = scope.query || '';

                    scope.clear = function() {
                        scope.query = '';
                        scope.collection.filter = {};
                        scope.collection.update();
                    };

                    scope.filter = function() {
                        if (!scope.query.trim()) {
                            scope.collection.filter = {};
                        } else {
                            scope.collection.filter = {
                                _string: scope.query
                            };
                        }

                        scope.collection.update();
                    };
                }
            };
        });
})();
