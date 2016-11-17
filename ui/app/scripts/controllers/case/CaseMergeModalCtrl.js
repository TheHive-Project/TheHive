(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseMergeModalCtrl', CaseMergeModalCtrl);

    function CaseMergeModalCtrl($state, $modalInstance, $q, SearchSrv, CaseSrv, UserInfoSrv, AlertSrv, caze, $http) {
        var me = this;

        this.caze = caze;
        this.pendingAsync = false;
        this.search = {
            type: 'title',
            placeholder: 'Search by case title',            
            minInputLength: 1,
            input: null,
            cases: []
        };
        this.getUserInfo = UserInfoSrv;

        this.getCaseByTitle = function(type, input) {
            var defer = $q.defer();

            SearchSrv(function (data /*, total*/ ) {                
                defer.resolve(data);
            }, {
                _string: (type === 'title') ? ('title:"' + input + '"') : ('caseId:' + input)
            }, 'case', 'all');

            return defer.promise;
        }

        this.format = function(caze) {
            if(caze) {
                return '#' + caze.caseId  + ' - ' + caze.title;
            }
            return null;            
        }

        this.clearSearch = function() {
            this.search.input = null;
            this.search.cases = [];
        }

        this.onTypeChange = function(type) {
            this.clearSearch();

            this.search.placeholder = 'Search by case ' + type;

            if(type === 'title') {
                this.search.minInputLength = 3;                
            } else if(type === 'number') {
                this.search.minInputLength = 1;
            }
        }

        this.onSelect = function(item, model, label) {            
            this.search.cases = [item];
        }

        this.merge = function () {
            // TODO pass params as path params not query params
            this.pendingAsync = true;
            CaseSrv.merge({}, {
                caseId: me.caze.id,
                mergedCaseId: me.search.cases[0].id
            }, function (merged) {

                $state.go('app.case.details', {
                    caseId: merged.id
                });

                $modalInstance.dismiss();

                AlertSrv.log('The cases have been successfully merged into a new case #' + merged.caseId, 'success');
            }, function (response) {
                this.pendingAsync = false;
                AlertSrv.error('CaseMergeModalCtrl', response.data, response.status);
            });
        };

        this.cancel = function () {
            $modalInstance.dismiss();
        };      
    }
})();
