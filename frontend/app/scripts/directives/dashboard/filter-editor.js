(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('filterEditor', function($q, AuthenticationSrv, UserSrv, TagSrv, UtilsSrv) {
        return {
            restrict: 'E',
            scope: {
                filter: '=?',
                entity: '=',
                metadata: '='
            },
            templateUrl: 'views/directives/dashboard/filter-editor.html',
            link: function(scope) {
                scope.dateOperator = {
                    custom: 'Custom',
                    today: 'Today',
                    last7days: 'Last 7 days',
                    last30days: 'Last 30 days',
                    last3months: 'Last 3 months',
                    last6months: 'Last 6 months',
                    lastyear: 'Last year'
                };

                scope.setDateFilterOperator = function(filter, operator) {
                    operator = operator || 'custom';

                    var dateRange = UtilsSrv.getDateRange(operator);

                    if(operator === 'custom') {
                        filter.value = {
                            operator: operator,
                            from: dateRange.from,
                            to: dateRange.to
                        };
                    } else {
                        filter.value = {
                            operator: operator,
                            from: null,
                            to: null
                        };
                    }

                };

                scope.editorFor = function(filter) {
                    if (filter.type === null) {
                        return;
                    }
                    var field = scope.metadata[scope.entity].attributes[filter.field];

                    if(!field) {
                        return;
                    }

                    if(field.name === 'tags') {
                        return field.name;
                    }

                    var type = field.type;

                    if ((type === 'string' || type === 'number' || type === 'integer'  || type === 'float' ) && field.values.length > 0) {
                        return 'enumeration';
                    }

                    return filter.type;
                };

                scope.promiseFor = function(filter, query) {
                    var field = scope.metadata[scope.entity].attributes[filter.field];

                    var promise = null;

                    if(field.name === 'tags') {
                        return TagSrv.getTagsFor(scope.entity, query);
                    } else if(field.type === 'user') {
                        promise = AuthenticationSrv.current()
                            .then(function(user) {
                                return UserSrv.autoComplete(user.organisation, query);
                            });
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
