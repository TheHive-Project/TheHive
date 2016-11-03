(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('PSearchSrv', function(SearchSrv, StreamSrv) {
            function update(objectType, control) {
                var range = '';
                if (control.loadAll) {
                    range = 'all';
                } else {
                    var end = control.currentPage * control.pageSize;
                    var start = end - control.pageSize;
                    range = start + '-' + end;
                }
                var filter;
                if (angular.isString(control.filter) && control.filter.length > 0 &&
                    angular.isString(control.baseFilter) && control.baseFilter.length > 0) {
                    filter = {
                        _string: '(' + control.filter + ') AND (' + control.baseFilter + ')'
                    };
                } else {
                    filter = _.without([control.filter, control.baseFilter], null, undefined, {}, '');
                    filter = filter.length === 0 ? {
                        '_any': '*'
                    } : {
                        _and: filter
                    };
                }
                SearchSrv(function(data, total) {
                    if (control.loadAll) {
                        control.allValues.length = 0;
                        angular.forEach(data, function(d) {
                            control.allValues.push(d);
                        });
                        changePage(control);
                    } else {
                        control.values.length = 0;
                        angular.forEach(data, function(d) {
                            control.values.push(d);
                        });
                        if (angular.isFunction(control.onUpdate)) {
                            control.onUpdate();
                        }
                    }
                    control.total = total;
                }, filter, objectType, range, control.sort, control.nparent, control.nstats);
            }

            function changePage(control) {
                if (control.loadAll) {
                    control.values.length = 0;
                    var end = control.currentPage * control.pageSize;
                    var start = end - control.pageSize;
                    angular.forEach(control.allValues.slice(start, end), function(d) {
                        control.values.push(d);
                    });
                } else {
                    control.update();
                }

                if (angular.isFunction(control.onUpdate)) {
                    control.onUpdate();
                }
            }

            /**
             * [function description]
             * @param  {String} root
             * @param  {String} objectType
             * @param  {Object} control
             *
             * @return {Object}
             */
            return function(root, objectType, control) {
                control.values = [];
                control.allValues = [];
                control.total = 0;
                control.currentPage = 1;
                control.update = function() {
                    update(objectType, control);
                };
                control.changePage = function() {
                    changePage(control);
                };
                if (!angular.isNumber(control.pageSize)) {
                    control.pageSize = 10;
                }
                if (control.loadAll !== true) {
                    control.loadAll = false;
                }

                if (!angular.isString(root)) {
                    root = 'any';
                }

                if (control.skipStream !== true) {
                    StreamSrv.listen(root, control.streamObjectType || objectType, function() {
                        update(objectType, control);
                    });
                }

                update(objectType, control);
                return control;
            };
        });
})();
