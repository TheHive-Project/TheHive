(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseMergeModalCtrl', CaseMergeModalCtrl);

    function CaseMergeModalCtrl($uibModalInstance, $q, QuerySrv, UserSrv, NotificationSrv, source, title, prompt, filter) {
        var me = this;

        this.source = source;
        this.caze = source;
        this.title = title;
        this.prompt = prompt;
        this.search = {
            type: 'title',
            placeholder: 'Search by case title. "Ex: Malware*"',
            minInputLength: 1,
            input: null,
            cases: []
        };
        this.getUserInfo = UserSrv.getCache;

        this.getCaseList = function (type, input) {
            var defer = $q.defer();

            var selectionFilter = (type === 'title') ? {
                _like: {
                    _field: 'title',
                    _value: input
                }
            } : {
                _field: 'number',
                _value: Number.parseInt(input)
            };

            var caseFilter;

            if (filter) {
                caseFilter = {
                    _and: [selectionFilter, filter]
                }
            } else {
                caseFilter = selectionFilter
            }

            QuerySrv.call('v1',
                [{ _name: 'listCase' }],
                {
                    filter: caseFilter,
                    name: 'get-case-for-merge'
                }
            ).then(function (data) {
                defer.resolve(data);
            }); // TODO add error handler

            return defer.promise;
        };

        this.format = function (caze) {
            if (caze) {
                return '#' + caze.number + ' - ' + caze.title;
            }
            return null;
        };

        this.clearSearch = function () {
            this.search.input = null;
            this.search.cases = [];
        };

        this.onTypeChange = function (type) {
            this.clearSearch();

            this.search.placeholder = 'Search by case ' + type;

            if (type === 'title') {
                this.search.minInputLength = 3;
            } else if (type === 'number') {
                this.search.minInputLength = 1;
            }
        };

        this.onSelect = function (item /*, model, label*/) {
            this.search.cases = [item];
        };

        this.merge = function () {
            $uibModalInstance.close(me.search.cases[0]);
        };

        this.cancel = function () {
            $uibModalInstance.dismiss();
        };
    }
})();
