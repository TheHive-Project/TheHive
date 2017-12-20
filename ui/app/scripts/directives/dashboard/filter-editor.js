(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('filterEditor', function($q, UserSrv) {
        return {
            restrict: 'E',
            scope: {
                filter: '=?',
                entity: '=',
                metadata: '='
            },
            templateUrl: 'views/directives/dashboard/filter-editor.html',
            link: function(scope) {
                scope.editorFor = function(filter) {
                    if (filter.type === null) {
                        return;
                    }
                    var field = scope.metadata[scope.entity].attributes[filter.field];

                    if(!field) {
                        return;
                    }
                    var type = field.type;

                    if ((type === 'string' || type === 'number') && field.values.length > 0) {
                        return 'enumeration';
                    }

                    return filter.type;
                };

                scope.promiseFor = function(filter, query) {
                    var field = scope.metadata[scope.entity].attributes[filter.field];

                    var promise = null;

                    if(field.type === 'user') {
                        promise = UserSrv.autoComplete(query);
                    } else if (field.values.length > 0) {
                        promise = $q.resolve(
                            _.map(field.values, function(item, index) {
                                return {
                                    text: item,
                                    label: field.labels[index] || item
                                };
                            })
                        );
                    } else {
                        promise = $q.resolve([]);
                    }

                    return promise.then(function(response) {
                        var list = [];

                        list = _.filter(response, function(item) {
                            var regex = new RegExp(query, 'gi');
                            return regex.test(item.label);
                        });

                        return $q.resolve(list);
                    });
                };
            }
        };
    });
})();
