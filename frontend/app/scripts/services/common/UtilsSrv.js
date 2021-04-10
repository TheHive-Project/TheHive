(function () {
    'use strict';
    angular.module('theHiveServices')
        .factory('UtilsSrv', function ($location) {
            var sensitiveTypes = ['url', 'ip', 'mail', 'domain', 'filename'];

            var service = {
                guid: function () {
                    function s4() {
                        return Math.floor((1 + Math.random()) * 0x10000)
                            .toString(16)
                            .substring(1);
                    }
                    return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
                        s4() + '-' + s4() + s4() + s4();
                },
                objectify: function (arr, property) {
                    return _.map(arr, function (str) {
                        var obj = {};
                        obj[property] = str;
                        return obj;
                    });
                },

                fangValue: function (value) {
                    return value
                        .replace(/\[\.\]/g, ".")
                        .replace(/hxxp/gi, "http")
                        .replace(/\./g, "[.]")
                        .replace(/http/gi, "hxxp");
                },

                fang: function (observable) {
                    if (sensitiveTypes.indexOf(observable.dataType) === -1) {
                        return observable.data;
                    }

                    return service.fangValue(observable.data);
                },

                unfang: function (observable) {
                    return observable.data
                        .replace(/\[\.\]/g, ".")
                        .replace(/hxxp/gi, "http");
                },

                shallowClearAndCopy: function (src, dst) {
                    dst = dst || {};

                    angular.forEach(dst, function (value, key) {
                        delete dst[key];
                    });

                    for (var key in src) {
                        if (src.hasOwnProperty(key) && !(key.charAt(0) === '$' && key.charAt(1) === '$')) {
                            dst[key] = src[key];
                        }
                    }
                    return dst;
                },

                updatableLink: function (scope, element, attrs) {
                    scope.updatable = {
                        'updating': false
                    };
                    scope.oldValue = scope.value;
                    if (!angular.isDefined(scope.active)) {
                        scope.active = false;
                    }
                    scope.edit = function () {
                        scope.updatable.updating = true;
                    };
                    scope.update = function (newValue) {
                        if (angular.isDefined(newValue)) {
                            scope.value = newValue;
                        }
                        if (angular.isDefined(attrs.onUpdate)) {
                            var updateResult = scope.onUpdate({
                                'newValue': scope.value
                            });
                            if (angular.isDefined(updateResult) && angular.isDefined(updateResult.$promise)) {
                                updateResult = updateResult.$promise;
                            }
                            if (angular.isObject(updateResult) && angular.isFunction(updateResult.then)) {
                                updateResult.then(function () {
                                    scope.oldValue = scope.value;
                                    scope.active = false;
                                    scope.format = 'static';
                                }, function () {
                                    scope.value = scope.oldValue;
                                });
                            } else {
                                scope.oldValue = scope.value;
                                scope.active = false;
                                scope.format = 'static';
                            }
                        }
                        scope.updatable.updating = false;
                    };
                    scope.cancel = function () {
                        scope.value = scope.oldValue;
                        scope.updatable.updating = false;
                    };
                    scope.clear = function () {
                        scope.value = null;
                    };
                },

                extractQueryParam: function (paramName, queryString) {
                    if (!queryString || !paramName) {
                        return;
                    }

                    var param = $location.search()[paramName];

                    if (param) {
                        return param;
                    } else {
                        var parsedQuery = _.find(queryString.split('&'), function (str) {
                            return str.startsWith(paramName + '=');
                        });
                        return parsedQuery ? parsedQuery.substr(paramName.length + 1) : undefined;
                    }
                },

                getDateRange: function (operator) {
                    var from;

                    switch (operator) {
                        case 'last7days':
                            from = moment().subtract(7, 'days');
                            break;
                        case 'last30days':
                            from = moment().subtract(30, 'days');
                            break;
                        case 'last3months':
                            from = moment().subtract(3, 'months');
                            break;
                        case 'last6months':
                            from = moment().subtract(6, 'months');
                            break;
                        case 'lastyear':
                            from = moment().subtract(1, 'years');
                            break;
                        case 'today':
                        default:
                            from = moment();
                            break;
                    }

                    var to = moment();

                    return {
                        from: from.hour(0).minutes(0).seconds(0).toDate(),
                        to: to.hour(23).minutes(59).seconds(59).toDate()
                    };
                },

                hasAddDeleteEvents: function (updates) {
                    if (!updates || updates.length === 0)
                        return;

                    return _.without(_.map(updates, function (u) {
                        return u.base.operation;
                    }), 'Update').length > 0;
                }
            };

            return service;
        });
})();
